package de.peran.utils;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.peran.DependencyStatisticAnalyzer;
import de.peran.generated.Versiondependencies;
import de.peran.reduceddependency.ChangedTraceTests;

/**
 * Divides the versions of a dependencyfile (and optionally an executionfile) in order to start slurm test executions.
 * @author reichelt
 *
 */
public class DivideVersions {
	public static void main(final String[] args) throws JAXBException, ParseException, JsonParseException, JsonMappingException, IOException {
		final Options options = OptionConstants.createOptions(OptionConstants.DEPENDENCYFILE, OptionConstants.EXECUTIONFILE);
		final CommandLineParser parser = new DefaultParser();

		final CommandLine line = parser.parse(options, args);

		final File dependencyFile = new File(line.getOptionValue(OptionConstants.DEPENDENCYFILE.getName()));
		final Versiondependencies dependencies = DependencyStatisticAnalyzer.readVersions(dependencyFile);
		String url = dependencies.getUrl().replaceAll("\n", "").replaceAll(" ", "");

		ChangedTraceTests changedTests;
		if (line.hasOption(OptionConstants.EXECUTIONFILE.getName())) {
			final ObjectMapper mapper = new ObjectMapper();
			ChangedTraceTests testsTemp;
			try {
				testsTemp = mapper.readValue(new File(line.getOptionValue(OptionConstants.EXECUTIONFILE.getName())), ChangedTraceTests.class);
			} catch (JsonMappingException e) {
				ObjectMapper objectMapper = new ObjectMapper();
				SimpleModule module = new SimpleModule();
				module.addDeserializer(ChangedTraceTests.class, new ChangedTraceTests.Deserializer());
				objectMapper.registerModule(module);
				testsTemp = objectMapper.readValue(new File(line.getOptionValue(OptionConstants.EXECUTIONFILE.getName())), ChangedTraceTests.class);
			}
			changedTests = testsTemp;
		} else {
			changedTests = null;
		}
		
		System.out.println("timestamp=$(date +%s)");
		for (int i = 0; i < dependencies.getVersions().getVersion().size(); i++) {
			final String endversion = dependencies.getVersions().getVersion().get(i).getVersion();
			// System.out.println("-startversion " + startversion + " -endversion " + endversion);
			if (changedTests == null || (changedTests != null && changedTests.getVersions().containsKey(endversion))) {
				System.out.println(
						"sbatch --nice=1000000 --time=10-0 "
								+ "--output=/newnfs/user/do820mize/processlogs/process_" + i + "_$timestamp.out "
						+ "--workdir=/newnfs/user/do820mize "
						+ "--export=PROJECT="+url+",HOME=/newnfs/user/do820mize,START="
								+ endversion + ",END=" + endversion + ",INDEX="+i+" executeTests.sh");
			}
		}
	}
}
