package com.ttl.internal.vn.tool.builder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import javax.swing.UnsupportedLookAndFeelException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.errors.TransportException;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool;
import com.ttl.internal.vn.tool.builder.cli.CliBuildTool.DefaultCliGetArtifactInfo;
import com.ttl.internal.vn.tool.builder.component.BuildTool;

public class App {
        private static final Options options = new Options() {
                {
                        addOption(Option.builder()
                                        .desc("Use this build tool in UI mode, no other command line arguments are required when running in UI mode. Default: false")
                                        .option("ui")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Clone the repository from remote or just open local repo. Default: false")
                                        .option("clone")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The repository to clone from. Required when clone is true")
                                        .option("repo")
                                        .longOpt("repository")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The directory to which the repository will be cloned to. Or the local repo directory")
                                        .longOpt("clonedDir")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The username to clone or fetch from the repository")
                                        .longOpt("gitUser")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The password to clone or fetch from to the repository")
                                        .longOpt("gitPassword")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The commit that will be used as the base for the build. The artifact produced will be built using the diff from baseCommit to targetCommit")
                                        .longOpt("baseCommit")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The commit that is the target of the build")
                                        .longOpt("targetCommit")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The folder that will be used to save the build artifacts")
                                        .longOpt("artifactFolder")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Whether it will run maven clean")
                                        .option("mavenClean")
                                        .required(false)
                                        .hasArg(false)
                                        .build());  
                        addOption(Option.builder()
                                        .desc("Whether it will update snapshot when running maven compile")
                                        .option("updateSnapshot")
                                        .required(false)
                                        .hasArg(false)
                                        .build());              
                        addOption(Option.builder()
                                        .desc("Whether it will build the configuration jar")
                                        .option("buildConfigJar")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Whether it will build the patch")
                                        .option("buildPatch")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Whether it will build the release package. NOTE: this will override the -buildConfigJar and -buildPatch flag")
                                        .option("buildReleasePackage")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The pom file that will be used to calculate the build order of the project artifacts. This is required for building the subproject correctly and getting the classpaths and setting the artifact name. Default: ${clonedDir}/pom.xml")
                                        .longOpt("projectPom")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The settings.xml file that will be used to fetch the remote dependencies. Default: ~/.m2/settings.xml")
                                        .longOpt("m2SettingsXml")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Whether to fetch latest updated from repo with every build. Default: false")
                                        .option("fetch")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Config prefixes which is going to be used to filter config changes, multiple prefixes could be specified by using comma. Default: config/,config_company/")
                                        .longOpt("configPrefixes")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Database change prefixes which is going to be used to filter database changes, multiple prefixes could be specified by using comma. Default: DatabaseChange/")
                                        .longOpt("databaseChangePrefixes")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Log directory. Default: ${tempDirectory}")
                                        .longOpt("logDirectory")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                }
        };

        // TODO: Add progress bar
        // TOO: Default to use pom.xml if not explicitly specified pom.xml
        // TODO: Add note.txt for delete change types
        // TODO: Add fetch button -> implemented, test basically
        public static void main(String[] args) throws ParseException, InvalidRemoteException, TransportException,
                        GitAPIException, IOException, ClassNotFoundException, MavenInvocationException,
                        ModelBuildingException,
                        UnsupportedLookAndFeelException {
                CommandLine commandLine = parseArgs(args, options);

                configureLog4j2(commandLine.getOptionValue("logDirectory", System.getProperty("java.io.tmpdir")));

                boolean uiMode = commandLine.hasOption("ui");

                if (uiMode) {
                        BuildTool buildTool = new BuildTool();
                        buildTool.setVisible(true);
                } else {
                        boolean clone = commandLine.hasOption("clone");
                        String repoURI = commandLine.getOptionValue("repository");
                        File clonedDir = new File(commandLine.getOptionValue("clonedDir"));
                        String gitUsername = commandLine.getOptionValue("gitUser");
                        String gitPassword = commandLine.getOptionValue("gitPassword");
                        String baseCommit = commandLine.getOptionValue("baseCommit");
                        String targetCommit = commandLine.getOptionValue("targetCommit");
                        File artifactFolder = new File(commandLine.getOptionValue("artifactFolder"));
                        boolean buildConfigJar = commandLine.hasOption("buildConfigJar");
                        boolean buildPatch = commandLine.hasOption("buildPatch");
                        boolean buildReleasePackage = commandLine.hasOption("buildReleasePackage");
                        File projectPom = Optional.ofNullable(commandLine.getOptionValue("projectPom")).map(File::new).orElse(null);
                        File m2SettingsXml = new File(commandLine.getOptionValue("m2SettingsXml"));
                        boolean fetch = commandLine.hasOption("fetch");
                        String databaseChangePrefixes = commandLine.getOptionValue("databaseChangePrefixes");
                        String configPrefixes = commandLine.getOptionValue("configPrefixes");
                        boolean updateSnapshot = commandLine.hasOption("updateSnapshot");
                        boolean mavenClean = commandLine.hasOption("mavenClean");
                        try (CliBuildTool cliBuildTool = new CliBuildTool(
                                        clone,
                                        repoURI,
                                        clonedDir,
                                        gitUsername,
                                        gitPassword,
                                        baseCommit,
                                        targetCommit,
                                        artifactFolder,
                                        mavenClean,
                                        updateSnapshot,
                                        buildConfigJar,
                                        buildReleasePackage,
                                        buildPatch,
                                        configPrefixes,
                                        databaseChangePrefixes,
                                        projectPom,
                                        m2SettingsXml,
                                        DefaultCliGetArtifactInfo::getArtifactInfo)) {
                                cliBuildTool.startBuildEnvironment();
                                cliBuildTool.build(fetch);
                        }
                }
        }

        public static CommandLine parseArgs(String[] args, Options options) throws ParseException {
                try {
                        CommandLineParser parser = new DefaultParser();
                        return parser.parse(options, args);
                } catch (ParseException e) {
                        System.out.println(e.getMessage());
                        HelpFormatter formatter = new HelpFormatter();
                        formatter.printHelp("java -jar ${artifact}.jar <options> | java -cp <classpaths> "
                                        + App.class.getName() + " <options>", options);
                        System.exit(1);
                        return null;
                }
        }

        private static void configureLog4j2(String logDirectory) {
                File logDirectoryFolder;
                if (StringUtils.isBlank(logDirectory) || !new File(logDirectory).isDirectory()) {
                        System.out.println("The log directory not exists using default configuration");
                        logDirectoryFolder = new File(System.getProperty("java.io.tmpdir"));
                } else {
                        logDirectoryFolder = new File(logDirectory);
                }

                ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory
                                .newConfigurationBuilder();

                builder.setStatusLevel(Level.INFO);
                builder.setConfigurationName("CustomConfig");

                ComponentBuilder<?> triggeringPolicyBuilder = builder.newComponent("Policies")
                                .addComponent(builder.newComponent("TimeBasedTriggeringPolicy")
                                                .addAttribute("interval", "1")
                                                .addAttribute("modulate", "true"));

                ComponentBuilder<?> rolloverStrategyBuilder = builder.newComponent("DefaultRolloverStrategy")
                                .addAttribute("max", "10");

                // Create a console log appender
                builder.add(builder.newAppender("ConsoleAppender", "CONSOLE"));

                // Create a file appender with the desired log file
                builder.add(
                                builder.newAppender("RollingLogFileAppender", "RollingFile")
                                                .addAttribute("fileName",
                                                                new File(logDirectoryFolder, "error.log")
                                                                                .getAbsolutePath())
                                                .addComponent(rolloverStrategyBuilder)
                                                .addComponent(triggeringPolicyBuilder)
                                                .add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT,
                                                                Filter.Result.DENY))
                                                .add(builder.newLayout("PatternLayout")
                                                                .addAttribute("pattern",
                                                                                Paths.get(logDirectoryFolder
                                                                                                .getAbsolutePath(),
                                                                                                "%d %p %c{1.} [%t] %m%n")
                                                                                                .toFile()
                                                                                                .getAbsolutePath()))
                                                .addAttribute("filePattern", Paths.get(logDirectoryFolder
                                                                .getAbsolutePath(),
                                                                "error-%d{dd-MM-yy}.log.gz")
                                                                .toFile()
                                                                .getAbsolutePath()));

                builder.add(
                                builder.newRootLogger(Level.INFO)
                                                .add(builder.newAppenderRef("ConsoleAppender"))
                                                .add(builder.newAppenderRef("RollingLogFileAppender")));

                Configuration config = builder.build();

                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                ctx.start(config);
        }
}