/**
 * This file is part of PerAn.
 *
 * PerAn is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * PerAn is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with PerAn.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.dagere.peass.transformation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.dependency.changesreading.JavaParserProvider;
import de.dagere.peass.testtransformation.JUnitTestTransformer;
import org.apache.commons.io.FileUtils;
import org.hamcrest.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests the transformationmethode of classes.
 *
 * @author reichelt ExecutionConfig
 */
public class TestTransformationMethode {

   @TempDir
   public static File testFolder;

   private static final URL SOURCE = Thread.currentThread().getContextClassLoader().getResource("transformation");
   private static File RESOURCE_FOLDER;
   private static File SOURCE_FOLDER;

   private File testFile;

   @BeforeAll
   public static void initFolder() throws URISyntaxException, IOException {
      RESOURCE_FOLDER = Paths.get(SOURCE.toURI()).toFile();
      SOURCE_FOLDER = new File(testFolder, "src/test/java");
      FileUtils.copyFile(new File(RESOURCE_FOLDER, "pom.xml"),
            new File(testFolder, "pom.xml"));
   }

   @Disabled
   @ParameterizedTest
   @ValueSource(strings = { "10000", "100000" })
   public void testJUnit5Transformation(String value) throws IOException {
      final File old2 = new File(RESOURCE_FOLDER, "TestMe9.java");
      testFile = new File(SOURCE_FOLDER, "TestMe9.java");
      FileUtils.copyFile(old2, testFile);
      MeasurementConfig config = MeasurementConfig.DEFAULT;

      config.getExecutionConfig().setIncreaseVariableValues(Collections.singletonList("TestMe9.WAIT_FOR_INITIALIZATION:" + value));

      final JUnitTestTransformer tt = new JUnitTestTransformer(testFolder, config);
      tt.determineVersions(Arrays.asList(new File[] { testFolder }));
      tt.transformTests();

      final CompilationUnit cu = JavaParserProvider.parse(testFile);

      final ClassOrInterfaceDeclaration clazz = cu.getClassByName("TestMe9").get();
      Assert.assertNotNull(clazz);

      final List<MethodDeclaration> methodsByName = clazz.getMethodsByName("testMethod9");
      MatcherAssert.assertThat(methodsByName, Matchers.hasSize(1));

      final MethodDeclaration testMethod = methodsByName.get(0);

      final AnnotationExpr performanceTestAnnotation = testMethod.getAnnotationByName("PerformanceTest").get();
      Assert.assertNotNull(performanceTestAnnotation);

      MatcherAssert.assertThat(performanceTestAnnotation.getChildNodes(), hasAnnotation("iterations"));
      MatcherAssert.assertThat(performanceTestAnnotation.getChildNodes(), hasAnnotation("warmup"));

      for (final Node n : performanceTestAnnotation.getChildNodes()) {
         System.out.println(n);
      }

      String WAIT_FOR_INITIALIZATION = performanceTestAnnotation.getParentNode().get().getChildNodes().get(5).getChildNodes().get(0).toString();
      WAIT_FOR_INITIALIZATION = WAIT_FOR_INITIALIZATION.replaceAll("[^0-9?!\\.]", "");
      Assert.assertEquals(value, Long.valueOf(WAIT_FOR_INITIALIZATION).longValue());

   }

   @After
   public void cleanup() {
      testFile.delete();
   }

   public static Matcher<List<Node>> hasAnnotation(final String annotationName) {
      return new BaseMatcher<List<Node>>() {
         @Override
         public boolean matches(final Object item) {
            final List<Node> nodes = (List<Node>) item;
            boolean contained = false;
            for (final Node node : nodes) {
               if (node instanceof MemberValuePair) {
                  final MemberValuePair pair = (MemberValuePair) node;
                  if (annotationName.equals(pair.getName().getIdentifier())) {
                     contained = true;
                     break;
                  }
               }
               System.out.println(node.getClass());
            }
            return contained;
         }

         @Override
         public void describeTo(final Description description) {
            description.appendText("Expected an annotation with value ").appendValue(annotationName);
         }
      };
   }
}
