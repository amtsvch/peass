package de.dagere.peass.execution.gradle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GradleDaemonFileDeleter {

   private static final Logger LOG = LogManager.getLogger(GradleDaemonFileDeleter.class);

   public static void deleteDaemonFile(File regularLogFile) {
      try (BufferedReader reader = new BufferedReader(new FileReader(regularLogFile))) {

         String daemonLine = "";
         final String searchString = "The client will now receive all logging from the daemon (pid: ";
         final int maxSearchLines = 50;

         for (int i = 0; i < maxSearchLines; i++) {
            daemonLine = reader.readLine();
            if(daemonLine.contains(searchString)) {
               deleteDaemonFileByName(daemonLine.substring(daemonLine.lastIndexOf(" ") +1 ));
               return;
            }
         }
         LOG.debug("pid could not be found in first {} lines of logfile, logfile was not deleted!", maxSearchLines);

      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void deleteDaemonFile(String processOutput) {
      String[] lines = processOutput.split("\n");
      if (lines.length > 2) {
         String daemonLine = lines[2];
         String daemonFileName = daemonLine.substring(daemonLine.lastIndexOf(" ") + 1);
         deleteDaemonFileByName(daemonFileName);
      }
   }
   
   private static void deleteDaemonFileByName(String daemonFileName) {
      File daemonFile = new File(daemonFileName);
      if (daemonFile.exists()) {
         daemonFile.delete();
      }
   }
}
