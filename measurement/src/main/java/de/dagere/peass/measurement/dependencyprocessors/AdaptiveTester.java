package de.dagere.peass.measurement.dependencyprocessors;

import java.io.File;
import java.io.IOException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependencyprocessors.CommitComparatorInstance;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.measurement.dataloading.ResultLoader;
import de.dagere.peass.measurement.dependencyprocessors.helper.EarlyBreakDecider;
import de.dagere.peass.measurement.dependencyprocessors.helper.ProgressWriter;

public class AdaptiveTester extends DependencyTester {

   private static final Logger LOG = LogManager.getLogger(AdaptiveTester.class);

   private int finishedVMs = 0;

   public AdaptiveTester(final PeassFolders folders, final MeasurementConfig measurementConfig, final EnvironmentVariables env, CommitComparatorInstance comparator) {
      super(folders, measurementConfig, env, comparator);
   }

   @Override
   public void evaluate(final TestMethodCall testcase) throws IOException {
      initEvaluation(testcase);

      final File logFolder = folders.getMeasureLogFolder(configuration.getFixedCommitConfig().getCommit(), testcase);

      try (ProgressWriter writer = new ProgressWriter(folders.getProgressFile(), configuration.getVms())) {
         evaluateWithAdaption(testcase, logFolder, writer);
      }
   }

   protected void evaluateWithAdaption(final TestMethodCall testcase, final File logFolder, final ProgressWriter writer) throws IOException {
      currentChunkStart = System.currentTimeMillis();
      for (finishedVMs = 0; finishedVMs < configuration.getVms(); finishedVMs++) {
         long comparisonStart = System.currentTimeMillis();
         runOneComparison(logFolder, testcase, finishedVMs);

         final boolean savelyDecidable = checkIsDecidable(testcase, finishedVMs);

         if (savelyDecidable) {
            LOG.debug("Savely decidable - finishing testing");
            break;
         }

         final boolean shouldBreak = reductionManager.updateExecutions(testcase, finishedVMs, getCurrentOrganizer());
         if (shouldBreak) {
            LOG.debug("Too few executions possible - finishing testing.");
            break;
         }
         long durationInSeconds = (System.currentTimeMillis() - comparisonStart) / 1000;
         writer.write(durationInSeconds, finishedVMs);

         betweenVMCooldown();
      }
   }

   public int getFinishedVMs() {
      return finishedVMs;
   }

   @Override
   public boolean checkIsDecidable(final TestMethodCall testcase, final int vmid) {
      final boolean savelyDecidable;
      if (configuration.isEarlyStop()) {
         final ResultLoader loader = new ResultLoader(configuration);
         loader.loadData(folders, testcase, currentChunkStart);
         LOG.debug(loader.getStatisticsAfter());
         DescriptiveStatistics statisticsBefore = loader.getStatisticsBefore();
         DescriptiveStatistics statisticsAfter = loader.getStatisticsAfter();

         final EarlyBreakDecider decider = new EarlyBreakDecider(configuration, statisticsAfter, statisticsBefore);
         savelyDecidable = decider.isBreakPossible(vmid);
      } else {
         savelyDecidable = false;
      }
      return savelyDecidable;
   }
}
