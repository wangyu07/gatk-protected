/*
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.walkers.bqsr;

import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.SAMFileHeader;
import org.broadinstitute.sting.commandline.ArgumentCollection;
import org.broadinstitute.sting.gatk.CommandLineGATK;
import org.broadinstitute.sting.gatk.contexts.AlignmentContext;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.filters.MappingQualityUnavailableFilter;
import org.broadinstitute.sting.gatk.filters.MappingQualityZeroFilter;
import org.broadinstitute.sting.gatk.refdata.ReadMetaDataTracker;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.walkers.*;
import org.broadinstitute.sting.utils.BaseUtils;
import org.broadinstitute.sting.utils.recalibration.*;
import org.broadinstitute.sting.utils.recalibration.covariates.Covariate;
import org.broadinstitute.sting.utils.baq.BAQ;
import org.broadinstitute.sting.utils.classloader.GATKLiteUtils;
import org.broadinstitute.sting.utils.collections.Pair;
import org.broadinstitute.sting.utils.exceptions.ReviewedStingException;
import org.broadinstitute.sting.utils.exceptions.UserException;
import org.broadinstitute.sting.utils.help.DocumentedGATKFeature;
import org.broadinstitute.sting.utils.pileup.PileupElement;
import org.broadinstitute.sting.utils.sam.GATKSAMRecord;
import org.broadinstitute.sting.utils.sam.ReadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

/**
 * First pass of the base quality score recalibration -- Generates recalibration table based on various user-specified covariates (such as read group, reported quality score, machine cycle, and nucleotide context).
 *
 * <p>
 * This walker is designed to work as the first pass in a two-pass processing step. It does a by-locus traversal operating
 * only at sites that are not in dbSNP. We assume that all reference mismatches we see are therefore errors and indicative
 * of poor base quality. This walker generates tables based on various user-specified covariates (such as read group,
 * reported quality score, cycle, and context). Since there is a large amount of data one can then calculate an empirical
 * probability of error given the particular covariates seen at this site, where p(error) = num mismatches / num observations.
 * The output file is a table (of the several covariate values, num observations, num mismatches, empirical quality score).
 * <p>
 * Note: ReadGroupCovariate and QualityScoreCovariate are required covariates and will be added for the user regardless of whether or not they were specified.
 *
 * <p>
 *
 * <h2>Input</h2>
 * <p>
 * The input read data whose base quality scores need to be assessed.
 * <p>
 * A database of known polymorphic sites to skip over.
 * </p>
 *
 * <h2>Output</h2>
 * <p>
 * A GATK Report file with many tables:
 * <ol>
 *     <li>The list of arguments</li>
 *     <li>The quantized qualities table</li>
 *     <li>The recalibration table by read group</li>
 *     <li>The recalibration table by quality score</li>
 *     <li>The recalibration table for all the optional covariates</li>
 * </ol>
 *
 * The GATK Report is intended to be easy to read by humans or computers. Check out the documentation of the GATKReport to learn how to manipulate this table.
 * </p>
 *
 * <h2>Examples</h2>
 * <pre>
 * java -Xmx4g -jar GenomeAnalysisTK.jar \
 *   -T DelocalizedBaseRecalibrator \
 *   -I my_reads.bam \
 *   -R resources/Homo_sapiens_assembly18.fasta \
 *   -knownSites bundle/hg18/dbsnp_132.hg18.vcf \
 *   -knownSites another/optional/setOfSitesToMask.vcf \
 *   -o recal_data.grp
 * </pre>
 */

@DocumentedGATKFeature( groupName = "BAM Processing and Analysis Tools", extraDocs = {CommandLineGATK.class} )
// TODO -- did you really want to allow BAQ?
// TODO -- can you fix the commenting style here?  It's just painful
@BAQMode(ApplicationTime = BAQ.ApplicationTime.ON_INPUT, QualityMode = BAQ.QualityMode.ADD_TAG)
@By(DataSource.READS)
@ReadFilters({MappingQualityZeroFilter.class, MappingQualityUnavailableFilter.class})                                   // only look at covered loci, not every loci of the reference file
@Requires({DataSource.READS, DataSource.REFERENCE})                                         // filter out all reads with zero or unavailable mapping quality
@PartitionBy(PartitionType.READ)                                                                                       // this walker requires both -I input.bam and -R reference.fasta
public class DelocalizedBaseRecalibrator extends ReadWalker<Long, Long> implements TreeReducible<Long> {
    @ArgumentCollection
    private final RecalibrationArgumentCollection RAC = new RecalibrationArgumentCollection();                          // all the command line arguments for BQSR and it's covariates

    private QuantizationInfo quantizationInfo;                                                                          // an object that keeps track of the information necessary for quality score quantization

    private RecalibrationTables recalibrationTables;

    private Covariate[] requestedCovariates;                                                                            // list to hold the all the covariate objects that were requested (required + standard + experimental)

    private RecalibrationEngine recalibrationEngine;

    private int minimumQToUse;

    protected static final String COVARS_ATTRIBUTE = "COVARS";                                                          // used to store covariates array as a temporary attribute inside GATKSAMRecord.\

    private static final String NO_DBSNP_EXCEPTION = "This calculation is critically dependent on being able to skip over known variant sites. Please provide a VCF file containing known sites of genetic variation.";

    /**
     * Parse the -cov arguments and create a list of covariates to be used here
     * Based on the covariates' estimates for initial capacity allocate the data hashmap
     */
    public void initialize() {

        // check for unsupported access
        if (getToolkit().isGATKLite() && !getToolkit().getArguments().disableIndelQuals)
            throw new UserException.NotSupportedInGATKLite("base insertion/deletion recalibration is not supported, please use the --disable_indel_quals argument");

        if (RAC.FORCE_PLATFORM != null)
            RAC.DEFAULT_PLATFORM = RAC.FORCE_PLATFORM;

        if (RAC.knownSites.isEmpty() && !RAC.RUN_WITHOUT_DBSNP)                                                         // Warn the user if no dbSNP file or other variant mask was specified
            throw new UserException.CommandLineException(NO_DBSNP_EXCEPTION);

        if (RAC.LIST_ONLY) {
            RecalUtils.listAvailableCovariates(logger);
            System.exit(0);
        }
        RAC.recalibrationReport = getToolkit().getArguments().BQSR_RECAL_FILE;                                          // if we have a recalibration file, record it so it goes on the report table

        Pair<ArrayList<Covariate>, ArrayList<Covariate>> covariates = RecalUtils.initializeCovariates(RAC);       // initialize the required and optional covariates
        ArrayList<Covariate> requiredCovariates = covariates.getFirst();
        ArrayList<Covariate> optionalCovariates = covariates.getSecond();

        requestedCovariates = new Covariate[requiredCovariates.size() + optionalCovariates.size()];
        int covariateIndex = 0;
        for (final Covariate covariate : requiredCovariates)
            requestedCovariates[covariateIndex++] = covariate;
        for (final Covariate covariate : optionalCovariates)
            requestedCovariates[covariateIndex++] = covariate;

        logger.info("The covariates being used here: ");
        for (Covariate cov : requestedCovariates) {                                                                     // list all the covariates being used
            logger.info("\t" + cov.getClass().getSimpleName());
            cov.initialize(RAC);                                                                                        // initialize any covariate member variables using the shared argument collection
        }

        int numReadGroups = 0;
        for ( final SAMFileHeader header : getToolkit().getSAMFileHeaders() )
            numReadGroups += header.getReadGroups().size();
        recalibrationTables = new RecalibrationTables(requestedCovariates, numReadGroups);

        recalibrationEngine = initializeRecalibrationEngine();
        recalibrationEngine.initialize(requestedCovariates, recalibrationTables);

        minimumQToUse = getToolkit().getArguments().PRESERVE_QSCORES_LESS_THAN;
    }

    private RecalibrationEngine initializeRecalibrationEngine() {

        final Class recalibrationEngineClass = GATKLiteUtils.getProtectedClassIfAvailable(RecalibrationEngine.class);
        try {
            final Constructor constructor = recalibrationEngineClass.getDeclaredConstructor((Class[])null);
            constructor.setAccessible(true);
            return (RecalibrationEngine)constructor.newInstance();
        }
        catch (Exception e) {
            throw new ReviewedStingException("Unable to create RecalibrationEngine class instance " + recalibrationEngineClass.getSimpleName());
        }
    }

    private boolean isLowQualityBase( final GATKSAMRecord read, final int offset ) {
        return read.getBaseQualities()[offset] < minimumQToUse;
    }

    /**
     * For each read at this locus get the various covariate values and increment that location in the map based on
     * whether or not the base matches the reference at this particular location
     */
    public Long map( final ReferenceContext ref, final GATKSAMRecord read, final ReadMetaDataTracker metaDataTracker ) {

        RecalUtils.parsePlatformForRead(read, RAC);
        // BUGBUG: solid support should go here.
        read.setTemporaryAttribute(COVARS_ATTRIBUTE, RecalUtils.computeCovariates(read, requestedCovariates));

        final boolean[] skip = calculateSkipArray(read, metaDataTracker); // skip known sites of variation as well as low quality and non-regular bases
        final int[] isSNP = calculateIsSNP(read, ref);
        final int[] isInsertion = calculateIsIndel(read, EventType.BASE_INSERTION);
        final int[] isDeletion = calculateIsIndel(read, EventType.BASE_DELETION);
        final byte[] baqArray = calculateBAQArray(read);

        final double[] snpErrors = calculateFractionalErrorArray(skip, isSNP, baqArray);
        final double[] insertionErrors = calculateFractionalErrorArray(skip, isInsertion, baqArray);
        final double[] deletionErrors = calculateFractionalErrorArray(skip, isDeletion, baqArray);

        recalibrationEngine.updateDataForRead(read, snpErrors, insertionErrors, deletionErrors);

        return 1L;
    }

    protected boolean[] calculateSkipArray( final GATKSAMRecord read, final ReadMetaDataTracker metaDataTracker ) {
        final byte[] bases = read.getReadBases();
        final boolean[] skip = new boolean[bases.length];
        for( int iii = 0; iii < bases.length; iii++ ) {
            skip[iii] = !BaseUtils.isRegularBase(bases[iii]) | isLowQualityBase(read, iii) | metaDataTracker.getReadOffsetMapping().containsKey(iii);
        }
        return skip;
    }

    // BUGBUG: can be merged with calculateIsIndel
    protected static int[] calculateIsSNP( final GATKSAMRecord read, final ReferenceContext ref ) {
        final byte[] readBases = read.getReadBases();
        final byte[] refBases = ref.getBases();
        final int[] snp = new int[readBases.length];
        int readPos = 0;
        int refPos = 0;
        for ( final CigarElement ce : read.getCigar().getCigarElements() ) {
            final int elementLength = ce.getLength();
            switch (ce.getOperator()) {
                case M:
                case EQ:
                case X:
                    for( int iii = 0; iii < elementLength; iii++ ) {
                        snp[readPos] = ( BaseUtils.basesAreEqual(readBases[readPos], refBases[refPos]) ? 0 : 1 );
                        readPos++;
                        refPos++;
                    }
                    break;
                case D:
                case N:
                    refPos += elementLength;
                    break;
                case I:
                case S: // ReferenceContext doesn't have the soft clipped bases!
                    readPos += elementLength;
                    break;
                case H:
                case P:
                    break;
                default:
                    throw new ReviewedStingException("Unsupported cigar operator: " + ce.getOperator());
            }
        }
        return snp;
    }

    protected static int[] calculateIsIndel( final GATKSAMRecord read, final EventType mode ) {
        final byte[] readBases = read.getReadBases();
        final int[] indel = new int[readBases.length];
        int readPos = 0;
        for ( final CigarElement ce : read.getCigar().getCigarElements() ) {
            final int elementLength = ce.getLength();
            switch (ce.getOperator()) {
                case M:
                case EQ:
                case X:
                case S:
                {
                    for (int iii = 0; iii < elementLength; iii++) {
                        indel[readPos++] = 0;
                    }
                    break;
                }
                case D:
                {
                    final int pos = ( read.getReadNegativeStrandFlag() ? (readPos + 1 < indel.length ? readPos + 1 : readPos) : readPos );
                    indel[pos] = ( mode.equals(EventType.BASE_DELETION) ? 1 : 0 );
                    break;
                }
                case I:
                {
                    if( !read.getReadNegativeStrandFlag() ) {
                        indel[readPos] = ( mode.equals(EventType.BASE_INSERTION) ? 1 : 0 );
                    }
                    for (int iii = 0; iii < elementLength; iii++) {
                        indel[readPos++] = 0;
                    }
                    if( read.getReadNegativeStrandFlag()) {
                        indel[(readPos < indel.length ? readPos : readPos - 1)] = ( mode.equals(EventType.BASE_INSERTION) ? 1 : 0 );
                    }
                    break;
                }
                case N:
                case H:
                case P:
                    break;
                default:
                    throw new ReviewedStingException("Unsupported cigar operator: " + ce.getOperator());
            }
        }
        return indel;
    }

    protected static double[] calculateFractionalErrorArray( final boolean[] skip, final int[] errorArray, final byte[] baqArray ) {
        if( skip.length != errorArray.length || skip.length != baqArray.length ) {
            throw new ReviewedStingException("Array length mismatch detected. Malformed read?");
        }

        final byte NO_BAQ_UNCERTAINTY = (byte)'@';
        final int BLOCK_START_UNSET = -1;

        final double[] fractionalErrors = new double[baqArray.length];
        boolean inBlock = false;
        int blockStartIndex = BLOCK_START_UNSET;
        int iii;
        for( iii = 0; iii < fractionalErrors.length; iii++ ) {
            if( baqArray[iii] == NO_BAQ_UNCERTAINTY ) {
                if( !inBlock ) {
                    fractionalErrors[iii] = errorArray[iii];
                } else {
                    calculateAndStoreErrorsInBlock(iii, blockStartIndex, skip, errorArray, fractionalErrors);
                    inBlock = false; // reset state variables
                    blockStartIndex = BLOCK_START_UNSET; // reset state variables
                }
            } else {
                inBlock = true;
                if( blockStartIndex == BLOCK_START_UNSET ) { blockStartIndex = iii; }
            }
        }
        if( inBlock ) {
            calculateAndStoreErrorsInBlock(iii-1, blockStartIndex, skip, errorArray, fractionalErrors);
        }
        return fractionalErrors;
    }

    private static void calculateAndStoreErrorsInBlock( final int iii, final int blockStartIndex, final boolean[] skip, final int[] errorArray, final double[] fractionalErrors ) {
        int totalErrors = 0;
        for( int jjj = Math.max(0,blockStartIndex-1); jjj <= iii; jjj++ ) {
            totalErrors += ( skip[jjj] ? 0 : errorArray[jjj] );
        }
        for( int jjj = Math.max(0,blockStartIndex-1); jjj <= iii; jjj++ ) {
            fractionalErrors[jjj] = ((double) totalErrors) / ((double)(iii - Math.max(0,blockStartIndex-1) + 1));
        }
    }

    private byte[] calculateBAQArray( final GATKSAMRecord read ) {
        final byte[] tag = BAQ.getBAQTag(read);
        if( tag == null ) {
            throw new UserException.BadInput("This walker requires either that BAQ be turned on for input, i.e. -baq CALCULATE_AS_NECESSARY, " +
                    "or that the input BAM be previously BAQ'ed with a BAQ tag. The BAQ tag wasn't found for read: " + read);
        }
        return tag;
    }

    /**
     * Initialize the reduce step by returning 0L
     *
     * @return returns 0L
     */
    public Long reduceInit() {
        return 0L;
    }

    /**
     * The Reduce method doesn't do anything for this walker.
     *
     * @param mapped Result of the map. This value is immediately ignored.
     * @param sum    The summing CountedData used to output the CSV data
     * @return returns The sum used to output the CSV data
     */
    public Long reduce(Long mapped, Long sum) {
        sum += mapped;
        return sum;
    }

    public Long treeReduce(Long sum1, Long sum2) {
        sum1 += sum2;
        return sum1;
    }

    @Override
    public void onTraversalDone(Long result) {
        logger.info("Calculating quantized quality scores...");
        quantizeQualityScores();

        logger.info("Writing recalibration report...");
        generateReport();
        logger.info("...done!");

        if (!RAC.NO_PLOTS) {
            logger.info("Generating recalibration plots...");
            generatePlots();
        }

        logger.info("Processed: " + result + " reads");
    }

    private void generatePlots() {
        File recalFile = getToolkit().getArguments().BQSR_RECAL_FILE;
        if (recalFile != null) {
            RecalibrationReport report = new RecalibrationReport(recalFile);
            RecalUtils.generateRecalibrationPlot(RAC.RECAL_FILE, report.getRecalibrationTables(), recalibrationTables, requestedCovariates, RAC.KEEP_INTERMEDIATE_FILES);
        }
        else
            RecalUtils.generateRecalibrationPlot(RAC.RECAL_FILE, recalibrationTables, requestedCovariates, RAC.KEEP_INTERMEDIATE_FILES);
    }

    /**
     * go through the quality score table and use the # observations and the empirical quality score
     * to build a quality score histogram for quantization. Then use the QuantizeQual algorithm to
     * generate a quantization map (recalibrated_qual -> quantized_qual)
     */
    private void quantizeQualityScores() {
        quantizationInfo = new QuantizationInfo(recalibrationTables, RAC.QUANTIZING_LEVELS);
    }

    private void generateReport() {
        PrintStream output;
        try {
            output = new PrintStream(RAC.RECAL_FILE);
        } catch (FileNotFoundException e) {
            throw new UserException.CouldNotCreateOutputFile(RAC.RECAL_FILE, "could not be created");
        }

        RecalUtils.outputRecalibrationReport(RAC, quantizationInfo, recalibrationTables, requestedCovariates, output);
    }
}

