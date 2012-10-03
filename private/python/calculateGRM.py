import PlinkBedReader as PlinkReader
import argparse
import numpy
import math
import linear_old as linear

def parseArgs():
 parser = argparse.ArgumentParser(description='Parse out arguments for the generalized burden test')
 parser.add_argument("-grm",action='store',default=None,dest="grm",
               required=True,help="The file into which to write the GRM values.")
 parser.add_argument("-bedBase",action='store',default=None,dest="bedBase",
               required=True,help="The base of the bed (so 'xxxx' if file named 'xxxx.bed'). Requires a bim and fam with the same base name.")
 parser.add_argument("-intervals",action='store',default=None,dest="intervals",required=False,help="A file listing intervals over which the GRM should be calculated")
 parser.add_argument("-localCorrectionBP",action='store',default=-1,type=int,dest="localCorrectionBP",required=False,
         help="Use variants outside of the given intervals but within this many BP to correct for LD structure.")
 parser.add_argument("-globalCorrection",action='store',default=None,dest="globalCorrection",required=False,
         help="Include this list of variant IDs in LD correction")
 parser.add_argument("-minFrequency",action='store',default=1e-7,type=float,dest="minFrequency",required=False,
         help="The minimum frequency of a variant to use in the GRM calculation.")
 parser.add_argument("-maxFrequency",action='store',default=1.0-1e-7,type=float,dest="maxFrequency",required=False,
         help="The maximum frequency of a variant to use in the GRM calculation.")
 parser.add_argument("-minFrequencyCorrect",action='store',default=1e-7,type=float,dest="minFrequencyCorrect",required=False,
         help="The minimum frequency of a variant to use for local/global LD correction")
 parser.add_argument("-maxFrequencyCorrect",action='store',default=1.0-1e-7,type=float,dest="maxFrequencyCorrect",required=False,
         help="The maximum frequency of a variant to use for local/global LD correction")
 return parser.parse_args()

## stream in the SNPs, break them into bins of "independent correctors" and "dependent genotypes", and run a correction
def streamAndLocalCorrect(args,reader):
 """ Stream in genotypes from the reader and obtain dosages that have been locally corrected by regions outside of the GRM intervals.
     Variants are broken into two bins: "independent correctors" and "dependent genotypes".
     @args: An argument parser object. We will use the following arguments it contains
           + args.intervals - an interval list which contains those intervals the GRM should be calculated from 
           + args.localCorrectionBP - the number of BP such that, if a variant falls within X of an interval, it is used for local correction
     @reader: a PlinkBedReader that streams in genotypes from the bed file passed in
     @return: a list of corrected dosages. This is a list of tuples: List[(PlinkVariant,List[Float])]
 """
 ## todo -- enable the expansion factor to change based on allele frequency
 dependentGenotypeIntervals = getIntervals(args.intervals) 
 regressorVariants = list()
 variantsToCorrect = list() 
 dosages = dict()
 genotypes = getNextVariant(reader) 
 # this is a 2-ple: (PlinkVariant,List[PlinkGenotype])
 siteNo = 1
 while ( genotypes != None and len(dependentGenotypeIntervals) > 0):
  if ( siteNo % 1000 == 0 ):
   print("read : "+str(siteNo) + " at : " + str(genotypes[0].pos) + " accum : "+str(len(regressorVariants))+ " compute : "+str(len(variantsToCorrect)))
  accumulateVariant(genotypes,regressorVariants,variantsToCorrect,dependentGenotypeIntervals,args.localCorrectionBP) 
  doneIntervals = removeStaleIntervals(genotypes[0],regressorVariants,dependentGenotypeIntervals,args.localCorrectionBP)
  completedVariants = findCompletedVariants(doneIntervals,regressorVariants,variantsToCorrect,args.localCorrectionBP)
  removeStaleVariants(doneIntervals,dependentGenotypeIntervals,regressorVariants,variantsToCorrect,args.localCorrectionBP) 
  for regressionVariants in completedVariants:
   varToCorrect = regressionVariants[0]
   varsToCorrectWith = regressionVariants[1]
   correctedVar,correctedDosages = getCorrectedDosagesDebug(varToCorrect,varsToCorrectWith) 
   dosages[correctedVar] = correctedDosages
  genotypes = getNextVariant(reader)
  siteNo += 1
 # there may yet be intervals not totally complete
 for completedVariant in findCompletedVariants(dependentGenotypeIntervals,regressorVariants,variantsToCorrect,args.localCorrectionBP):
  varToCorrect = completedVariant[0]
  varsToUse = completedVariant[1]
  correctedVar,correctedDosages = getCorrectedDosagesDebug(varToCorrect,varsToUse)
  dosages[correctedVar]=correctedDosages
  # why is this a map?  
 return dosages

def getCorrectedDosagesDebug(responseVar,predictorVar):
 # just pass the response through normally, subtracting the mean
 respArray = numpy.array(list(map(lambda u: float(u.getDosage()),responseVar[1])))
 mean = numpy.sum(respArray)/respArray.size
 var = math.sqrt(mean*(1.0-mean))
 respArray -= mean
 respArray /= var
 return (responseVar[0], respArray)

##NEEDS_TEST
def getCorrectedDosages(responseVar,predictorVar):
 # workhorse method: take the response and predictor variants, and generate LD-corrected
 # dosages of the response variable via Logistic R or OLSR. todo -- hook this up to the command line.
 # 1: stick the response into a numpy vector array
 respArray = numpy.array(list(map(lambda u: u.getDosage(),responseVar[1])))
 # 2: stick the predictors into a numpy matrix array
 predArray = list(map(lambda u: numpy.array(list(map(lambda v: v.getDosage(),u[1]))),predictorVar))
 predArray.append(numpy.array(list(map(lambda x: 1.0, responseVar[1]))))
 predArray = numpy.array(predArray)
 # 3: if the predictor array is too large, do something; todo -- me
 # 4: run a regression on this
 regression = linear.GLM.Logistic.simpleNewton(respArray,predArray,N)
 return (responseVar[0],regression.residuals)


def globalCorrect(dosage,globalGenotypes):
 # another workhorse method: dosage contains residuals from earlier regression. Fit these with the global genotypes.
 predArray = numpy.array(list(map(lambda u: numpy.array(list(map(lambda v: v.getDosage(),u[1]))),globalGenotypes)))
 regression = linear.GLM.Logistic.simpleNewton(dosage,predArray,N)
 return regression.residuals

def globalCorrectDebug(dosage,globalGenotypes):
 # just a verison of the above that ignores regression for debugging purposes. Pass through the dosages.
 return [dosage[t] for t in sorted(dosage)]

def findCompletedVariants(completedIntervals,regressorVariants,variantsToCorrect,distance):
 """ for intervals that are complete, combine the nearby (within @distance bp) regressor variants and those variants to correct within the interval.
     This generates a list of (variantToCorrect,List[RegressorVariant]) pairs, one for each completed interval.
     @completedIntervals - a list of intervals that were determined complete. Will extract regressor/regressand variables based on this list. A list: List[(String,Int,Int)]
     @regressorVariants - a list of variants falling close to intervals to be used for local correction. A list: List[(PlinkVariant,List[PlinkGenotype])]
                           This algorithm will pair variants in this list with variants in @variantsToCorrect based on proximity to the same interval
     @variantsToCorrect - a list of variants falling inside intervals from which the corrected dosage is to be extracted. A list: List[(PlinkVariant,List[PlinkGenotype])]
                           This method will pair variants in this list with variants in @regressorVariants based on proximity to the same interval
     @returns - a list of pairs: (PlinkVariant,List[PlinkVariant]) extacted from the finished intervals representing a dosage-correction regression. E.g. for (Y,X): Y ~ X.
 """
 marshalledRegressions = list()
 for completedInterval in completedIntervals:
  # assemble those regressors that are nearby. Perhaps there's a faster way to do this (like a cached map from intervals to the first and last regressor variants close to it)
  intervalRegressors = list()
  for regressorGenotype in regressorVariants:
   regressorVariant = regressorGenotype[0]
   if ( calcDistanceToInterval(regressorVariant,completedInterval) < -distance ):
    break
   elif ( calcDistanceToInterval(regressorVariant,completedInterval) < distance ):
    intervalRegressors.append(regressorGenotype)
  # now pair the variants to correct in the interval with these regressors
  for correctGenotype in variantsToCorrect:
   if ( calcDistanceToInterval(correctGenotype[0],completedInterval) < 0 ):
    break
   elif ( calcDistanceToInterval(correctGenotype[0],completedInterval) == 0 ):
    marshalledRegressions.append((correctGenotype,intervalRegressors))
 return marshalledRegressions

def getRegressionVariants(variant,accumulator,distance):
 ## there is a faster way to do this by keeping track of the index of the variant in findCompletedVariants. If too slow can fix here.
 nearVariants = list()
 for v in accumulator:
  if ( abs(variant[0].distanceTo(v[0])) <= distance ):
   nearVariants.append(v)
 return (variant,nearVariants) 
 
def accumulateVariant(genotypes,correctorVariantsList,variantsToCorrectList,intervalsToCorrectList,distance):
 """ Takes a variant genotype and identifies the correct bin in which to put it. This identification is solely dependent on
     the distance between the variant and its closest interval. In particular:
      + > @distance BP == ignore
      + <= @distance BP, but > 0bp == it is a corrector variant
      + =0 BP == it is a variant to correct
     @genotypes - The variant site and genotypes to place into bin. A 2-ple (PlinkVariant,List[PlinkGenotype]).
     @correctorVariantsList - the list of variants to use for downstream genotype correction. A list: List[(PlinkVariant,List[PlinkGenotype])]
     @variantsToCorrectList - the list of variants to correct and get dosages for during downstream correction. A list: List[(PlinkVariant,List[PlinkGenotype])]
     @intervalsToCorrectList - the list of intervals that determine which variants to correct. A list: List[(String,Int,Int)]
     @distance - the number of BP from the start or end of an interval for which variants should be used for local correction. An Int.
 """
 freq = sum(map(lambda x: x.getDosage(),genotypes[1]))/len(genotypes[1])
 inInterval = False
 nearInterval = False
 variant = genotypes[0] # get the PlinkVariant
 for correctInterval in intervalsToCorrectList:
  distToInterval = calcDistanceToInterval(variant,correctInterval)
  if ( distToInterval > distance ):
   # more than @distance to the interval. Break.
   break
  elif ( distToInterval > 0 ):
   # within distance of the interval, but still before
   nearInterval = True
  elif ( distToInterval == 0 ):
   # inside of the interval
   inInterval = True
  elif ( distToInterval > -distance ):
   # after the interval, but still within distance
   nearInterval = True
  else:
   # way after the interval, continue
   continue
 if ( inInterval and freq < args.maxFrequency and freq > args.minFrequency ):
  variantsToCorrectList.append(genotypes)
 elif ( nearInterval and freq < args.maxFrequencyCorrect and freq > args.minFrequencyCorrect ):
  correctorVariantsList.append(genotypes)

def removeStaleIntervals(variant,correctorVariantsList,intervalsToCorrectList,distance):
 """ Examine the lists to identify, remove, and return intervals that are stale (e.g. intervals far enough from varaints as to never include a variant again)
     @variant - the most recent variant read from the bedFile. Used to cull intervals out.
     @correctorVariantsList - the list of variants to use for downstream genotype correction. A list: List[(PlinkVariant,List[PlinkGenotype])]. Remove variants if too far before first interval.
     @intervalsToCorrectList - the list of intervals that determine which variants to correct. A list: List[(String,Int,Int)]. Remove intervals if too far before current variant.
     @distance - the number of BP from the start or end of an interval for which variants should be used for local correction. An Int. 
     @return - a list of intervals removed
 """
 intervalsRemoved = list()
 while ( len(intervalsToCorrectList) > 0 and calcDistanceToInterval(variant,intervalsToCorrectList[0]) < -distance ):
  intervalsRemoved.append(intervalsToCorrectList.pop(0))
 return intervalsRemoved

def removeStaleVariants(completedIntervalsList,remainingIntervalsList,regressorVariantsList,variantsToCorrectList,distance):
 """ The completed intervals describe a set of variants that are done: namely all of the variants to correct within the
     interval, and those coming before it. Need to be careful about not removing anything involved in remaining intervals.
     @completedIntervalsList - a list of intervals for which we have seen a variant more than @distance after its end position. A list: List[(String,Int,Int)]
     @remainingIntervalsList - a list of intervals for which we have *not yet* seen a variant more than @istance after their end position. A list: List[(String,Int,Int)]
     @regressorVariantsList - a list of variants to use as independent variables for predicting variants in @variantsToCorrect. A list: List[(PlinkVariant,List[PlinkGenotype])]
                              This method will remove from this list all variants falling near the completed intervals and not within @distance of the remaining intervals
     @variantsToCorrectList - a list of variants from which to obtain corrected dosages via regression on @regressorVariantsList. A list: List[(PlinkVariant,List[PlinkGenotype])]
                              This method will remove from this list all variants falling inside of completed intervals.
     @distance - The number of BP from the start or end of an interval for which variants should be used for local correction. An Int.
     @return - nothing. But modify the lists above in the ways mentioned.
 """
 if ( len(remainingIntervalsList) == 0 ):
  # there are no more intervals. Clear everything.
  regressorVariantsList = list()
  variantsToCorrectList = list()
 else:
  firstRemainingInterval = remainingIntervalsList[0]
  for completedInterval in completedIntervalsList:
   # remove the variants near the interval
   while ( len(regressorVariantsList) > 0 and calcDistanceToInterval(regressorVariantsList[0][0],completedInterval) > -distance and
           calcDistanceToInterval(regressorVariantsList[0][0],firstRemainingInterval) > distance ):
    regressorVariantsList.pop(0)
   # remove the variants in the interval
   while ( len(variantsToCorrectList) > 0 and calcDistanceToInterval(variantsToCorrectList[0][0],completedInterval) == 0 ):
    variantsToCorrectList.pop(0)

def calcDistanceToInterval(var,interval):
 if ( var.chr != interval[0] ):
  return 500000000
 varpos = int(var.pos)
 if ( varpos > interval[2] ): # after the interval
  return -(varpos - interval[2])
 elif ( varpos < interval[1] ): # before the interval
  return interval[1]-varpos
 else:
  # must be in the interval
  return 0

def getIntervals(intervalFile):
 return list(map(lambda y: (y[0],int(y[1]),int(y[2])), map(lambda x: x.strip().split(), open(intervalFile).readlines())))

def getNextVariant(reader):
 # accumulate all samples into a single tuple of (common variant, list<genotype>)
 rpeek = reader.peek()
 if ( rpeek == None ):
  return None
 curVariant = rpeek.variant
 genotypes = list()
 while ( reader.peek() != None and reader.peek().variant == curVariant ):
  genotypes.append(reader.next())
 return (curVariant,genotypes)

## given the dosages, we want to further correct by some global set of SNPs (e.g. known causal/associated loci)
def globallyCorrectDosages(args,dosages):
 # need to read in the global variants. This means re-reading, but whatever.
 bedReader = PlinkReader.getReader(args.bedBase)
 # get the IDs of the global correctors
 globalIDs = set(map(lambda x: x.strip(),open(args.globalCorrection).readlines()))
 # do some matching
 globalGenotypes = list()
 var = getNextVariant(reader)
 while ( var != None ):
  if ( var[0].id in globalIDs ):
   globalGenotypes.append(var)
 # do some corrections
 correctedDosages = list()
 vars = set(dosages.keys())
 vars.sort()
 for v in vars:
  dosage = dosages[v]
  corrected = globalCorrectDebug(dosage,globalGenotypes) 
  correctedDosages.append(corrected)
 return correctedDosages

## now we want to actually compute the GRM
def printGRMFromDosages(args,correctedDosages):
 out = open(args.grm,'w')
 # the dosages come in as a list of lists, where correctedDosages[i] are the dosages for snp [i]. So for dosages[i][j] we need to iterate over [j] first.
 for samIdx1 in range(len(dosages[0])):
  for samIdx2 in range(samIdx1):
   nVariants,relatedness = calcDistance(samIdx1,samIdx2,correctedDosages)
   out.write("%d\t%d\t%d\t%.8e\n" % (samIdx1,samIdx2,nVariants,relatedness))

def calcDistance(idx1,idx2,dosages):
 # compute the relatedness between sample 1 and sample 2, given the dosages
 # dosages come in as a matrix dosage[snp][sample] = value
 ## note on this calculation:
 ## GCTA uses the formula Ajk = 1/N sum_i (Xij - 2pi)(Xik-2pi)/2pi(1-pi)
 ## the equivalent here would be to do the following:
 ###### numVar = len(dosages[0])
 ###### meanDos = map(lambda x: mean(dosages[x]),range(numVar))
 ###### sam1dos = map(lambda x: dosages[x][idx1]-2*meanDos[x],range(numVar))
 ###### sam2dos = map(lambda x: dosages[x][idx2]-2*meanDos[x],range(numVar))
 ###### components = map(lambda x: sam1dos[x]*sam2dos[x]/(2*meanDos[x]*(1-meanDos[x])),range(numVar))
 ###### relatedness = sum(components)/numVar
 ## however, it is not entirely clear that the assumptions built into this model
 ## -- namely either that effect size ~ sqrt(p(1-p)) or that variance = p(1-p) --
 ## should hold with LD-corrected dosages.
 ## for now - don't bother normalizing. The mean dosage should already be 0, from the regression.
 # todo -- make the behavior togglable via a command line argument.
 # todo -- this is just an inner product. numpy should be able to speed it up.
 numVar = len(dosages)
 return (numVar,sum(map(lambda x: dosages[x][idx1]*dosages[x][idx2],range(numVar)))/numVar)

def assertArgsAreGood(args):
 """ Assert that the command line arguments are within expected parameters.
 """
 if ( args.localCorrectionBP < 0 ):
  raise BaseError("The number of BP for local correction is 0 at minimum.")

 if ( args.minFrequency < 1e-9 or args.minFrequencyCorrect < 1e-9):
  raise BaseError("The minimum frequency must be >0")

 if ( args.maxFrequency > 1.0 - 1e-9 or args.maxFrequencyCorrect > 1.0-1e-9 ):
  raise BaseError("The maximum frequency must be <1")

if __name__ == "__main__":
 # get the arguments
 args = parseArgs()
 assertArgsAreGood(args)
 # get the reader
 bedReader = PlinkReader.getReader(args.bedBase)
 if ( not bedReader.snpMajor() ):
  raise BaseError("This tool currently only works with bed files in SNP-major mode. Please transpose the bed file.")
 # step 1: generate a locally-corrected per-sample dosages
 dosages = streamAndLocalCorrect(args,bedReader)
 # step 2: generate globally-corrected per-sample dosages
 if ( args.globalCorrection != None ):
  dosages = globallyCorrectDosages(args,dosages)
 else:
  dosages = [dosages[t] for t in dosages]
 # step 3: calculate the GRM and print it out
 printGRMFromDosages(args,dosages)
