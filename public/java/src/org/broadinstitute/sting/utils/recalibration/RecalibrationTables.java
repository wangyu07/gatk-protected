/*
 * Copyright (c) 2012 The Broad Institute
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

package org.broadinstitute.sting.utils.recalibration;

import com.google.java.contract.Ensures;
import org.broadinstitute.sting.utils.collections.LoggingNestedIntegerArray;
import org.broadinstitute.sting.utils.recalibration.covariates.Covariate;
import org.broadinstitute.sting.utils.collections.NestedIntegerArray;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Utility class to facilitate on-the-fly base quality score recalibration.
 *
 * User: ebanks
 * Date: 6/20/12
 */

public final class RecalibrationTables {
    public enum TableType {
        READ_GROUP_TABLE(0),
        QUALITY_SCORE_TABLE(1),
        OPTIONAL_COVARIATE_TABLES_START(2);

        public final int index;

        private TableType(final int index) {
            this.index = index;
        }
    }

    private final ArrayList<NestedIntegerArray<RecalDatum>> tables;
    private final int qualDimension;
    private final int eventDimension = EventType.values().length;
    private final int numReadGroups;
    private final PrintStream log;

    public RecalibrationTables(final Covariate[] covariates) {
        this(covariates, covariates[TableType.READ_GROUP_TABLE.index].maximumKeyValue() + 1, null);
    }

    public RecalibrationTables(final Covariate[] covariates, final int numReadGroups) {
        this(covariates, numReadGroups, null);
    }

    public RecalibrationTables(final Covariate[] covariates, final int numReadGroups, final PrintStream log) {
        tables = new ArrayList<NestedIntegerArray<RecalDatum>>(covariates.length);
        for ( int i = 0; i < covariates.length; i++ )
            tables.add(i, null); // initialize so we can set below

        qualDimension = covariates[TableType.QUALITY_SCORE_TABLE.index].maximumKeyValue() + 1;
        this.numReadGroups = numReadGroups;
        this.log = log;

        tables.set(TableType.READ_GROUP_TABLE.index,
                log == null ? new NestedIntegerArray<RecalDatum>(numReadGroups, eventDimension) :
                        new LoggingNestedIntegerArray<RecalDatum>(log, "READ_GROUP_TABLE", numReadGroups, eventDimension));

        tables.set(TableType.QUALITY_SCORE_TABLE.index, makeQualityScoreTable());

        for (int i = TableType.OPTIONAL_COVARIATE_TABLES_START.index; i < covariates.length; i++)
            tables.set(i,
                    log == null ? new NestedIntegerArray<RecalDatum>(numReadGroups, qualDimension, covariates[i].maximumKeyValue()+1, eventDimension) :
                            new LoggingNestedIntegerArray<RecalDatum>(log, String.format("OPTIONAL_COVARIATE_TABLE_%d", i - TableType.OPTIONAL_COVARIATE_TABLES_START.index + 1),
                                    numReadGroups, qualDimension, covariates[i].maximumKeyValue()+1, eventDimension));
    }

    @Ensures("result != null")
    public NestedIntegerArray<RecalDatum> getReadGroupTable() {
        return getTable(TableType.READ_GROUP_TABLE.index);
    }

    @Ensures("result != null")
    public NestedIntegerArray<RecalDatum> getQualityScoreTable() {
        return getTable(TableType.QUALITY_SCORE_TABLE.index);
    }

    @Ensures("result != null")
    public NestedIntegerArray<RecalDatum> getTable(final int index) {
        return tables.get(index);
    }

    @Ensures("result >= 0")
    public int numTables() {
        return tables.size();
    }

    /**
     * Allocate a new quality score table, based on requested parameters
     * in this set of tables, without any data in it.  The return result
     * of this table is suitable for acting as a thread-local cache
     * for quality score values
     * @return a newly allocated, empty read group x quality score table
     */
    public NestedIntegerArray<RecalDatum> makeQualityScoreTable() {
        return log == null
                ? new NestedIntegerArray<RecalDatum>(numReadGroups, qualDimension, eventDimension)
                : new LoggingNestedIntegerArray<RecalDatum>(log, "QUALITY_SCORE_TABLE", numReadGroups, qualDimension, eventDimension);
    }

    /**
     * Merge in the quality score table information from qualityScoreTable into this
     * recalibration table's quality score table.
     *
     * @param qualityScoreTable the quality score table we want to merge in
     */
    public void combineQualityScoreTable(final NestedIntegerArray<RecalDatum> qualityScoreTable) {
        RecalUtils.combineTables(getQualityScoreTable(), qualityScoreTable);
    }
}
