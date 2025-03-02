package de.dagere.peass.overviewTables;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.reader.CommitKeeper;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;

/**
 * Creates tab:projektgroessen of PhD thesis.
 * @author reichelt
 *
 */
public class GetProjectSizes {
   
   public static final String[] allProjects = new String[] { "commons-compress", "commons-csv", "commons-dbcp", "commons-fileupload", "commons-imaging", "commons-io",
                  "commons-numbers", "commons-text", "k-9", "commons-pool", "commons-jcs" };
   
   public static void main(final String[] args) throws JsonParseException, JsonMappingException, IOException {
      
      File dependencyFolder;
      if (System.getenv(Constants.PEASS_REPOS) != null) {
         final String repofolder = System.getenv(Constants.PEASS_REPOS);
         final File folder = new File(repofolder);
         dependencyFolder = new File(folder, "dependencies-final");
      } else {
         throw new RuntimeException("Please define environment variable " + Constants.PEASS_REPOS);
      }
      
      System.out.println("Projekt & Versionen & Analysierbar & Geändert & Selektiert & Tests\\\\ \\hline");
      for (final String project: allProjects) {
         final File projectFolder = new File("../../projekte/" + project);
         final int commits = GitUtils.getCommits(projectFolder, true).size();
         
         final File executionFile = new File(dependencyFolder, ResultsFolders.TRACE_SELECTION_PREFIX + project+".json");
         
         int analyzable = 0;
         
         int tests = 0;
         int executionTests = 0;
         if (executionFile.exists()) {
            final ExecutionData executionData = Constants.OBJECTMAPPER.readValue(executionFile, ExecutionData.class);
            executionTests = executionData.getCommits().size();
            for (final TestSet test : executionData.getCommits().values()) {
               tests += test.getTestMethods().size();
            }
         }
         
         final File nonRunning = new File(dependencyFolder, "nonRunning_" + project+".json");
         final File nonChanges = new File(dependencyFolder, "nonChanges_" + project+".json");
         
         int changes = 0; 
         if (nonRunning.exists()) {
            final CommitKeeper vk = Constants.OBJECTMAPPER.readValue(nonRunning, CommitKeeper.class);
            analyzable = commits - vk.getNonRunableReasons().size();
            if (nonChanges.exists()) {
               final CommitKeeper vk2 = Constants.OBJECTMAPPER.readValue(nonChanges, CommitKeeper.class);
               changes = analyzable - vk2.getNonRunableReasons().size();
            }
         }
         
         System.out.println(project + " & " + commits + " & " + analyzable +" & " + changes + " & "+ executionTests + " & " + tests + "\\\\");
      }
      
   }
}
