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
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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
                                        .desc("The ref (commit hash, branch name,..) that will be used as the base for the build. The artifact produced will be built using the diff from baseRef to targetRef")
                                        .longOpt("baseRef")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("The ref (commit hash, branch name,..) that is the target of the build")
                                        .longOpt("targetRef")
                                        .required(false)
                                        .hasArg(true)
                                        .numberOfArgs(1)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Use working directory as the target of the build, use this to quickly build a test package from working directory, you're expected to fix any compilation first before running with this option, you can use this in addition with entryFilter flag to exclude any erroneous entry from the artifact. NOTE: Setting this will ignore targetRef, patchFile and projectPom parameter")
                                        .option("useWorkingDirectory")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Filter erroneous diff with regex")
                                        .longOpt("entryFilter")
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
                                        .desc("Git patch file for fixing compilation before running the build. All entry specified in patch file will be excluded from the final artifact")
                                        .longOpt("patchFile")
                                        .required(false)
                                        .hasArg(true)
                                        .build());
                        addOption(Option.builder()
                                        .desc("Java home, if empty default java home will be used")
                                        .longOpt("javaHome")
                                        .required(false)
                                        .hasArg(true)
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
                        addOption(Option.builder()
                                        .desc("Whether it will ask for artifact info interactively")
                                        .option("interactive")
                                        .required(false)
                                        .hasArg(false)
                                        .build());
                }
        };

        // TODO: Add ability to cancel mid build -> Implemented
        // TODO: Add progress bar -> Implemented
        // TODO: Add note.txt for delete change types -> Implemented
        // TODO: Hide fetch button -> Implemented
        // TODO: Lock combobox from changing branch, build from chosen commit to working dir -> Implemented
        // TODO: Hide checkout btn -> Implemented
        // TODO: Do not create jar file for server build -> Implemented
        // TODO: Remove patch and config folder from artifact for release package build -> Implemented
        // TODO: Popup for artifact info or switch to new panel -> Implemented
        // TODO: Update README.MD
        // TODO: Remove Manifest from config jar -> Implemented
        // TODO: Recheck why branch that had been removed still show up in combobox -> Git problem, ignored
        // TODO: Add current working directory mode -> Implemented
        public static void main(String[] args) throws ParseException, InvalidRemoteException, TransportException,
                GitAPIException, IOException, ClassNotFoundException, MavenInvocationException,
                ModelBuildingException,
                UnsupportedLookAndFeelException, XmlPullParserException, InterruptedException {
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
                        String baseRef = commandLine.getOptionValue("baseRef");
                        String targetRef = commandLine.getOptionValue("targetRef");
                        File artifactFolder = new File(commandLine.getOptionValue("artifactFolder"));
                        boolean buildConfigJar = commandLine.hasOption("buildConfigJar");
                        boolean buildPatch = commandLine.hasOption("buildPatch");
                        boolean buildReleasePackage = commandLine.hasOption("buildReleasePackage");
                        File m2SettingsXml = new File(commandLine.getOptionValue("m2SettingsXml"));
                        boolean fetch = commandLine.hasOption("fetch");
                        String databaseChangePrefixes = commandLine.getOptionValue("databaseChangePrefixes");
                        String configPrefixes = commandLine.getOptionValue("configPrefixes");
                        boolean updateSnapshot = commandLine.hasOption("updateSnapshot");
                        File patchFile = Optional.ofNullable(commandLine.getOptionValue("patchFile"))
                                        .map(File::new)
                                        .filter(File::isFile)
                                        .orElse(null);
                        boolean interactive = commandLine.hasOption("interactive");
                        File javaHome = Optional.ofNullable(commandLine.getOptionValue("javaHome"))
                                        .map(File::new)
                                        .filter(File::isFile)
                                        .orElse(null);
                        boolean useWorkingDirectory = commandLine.hasOption("useWorkingDirectory");
                        String entryFilter = commandLine.getOptionValue("entryFilter");
                        try (CliBuildTool cliBuildTool = new CliBuildTool(
                                        clone,
                                        repoURI,
                                        clonedDir,
                                        gitUsername,
                                        gitPassword,
                                        baseRef,
                                        targetRef,
                                        entryFilter,
                                        artifactFolder,
                                        updateSnapshot,
                                        buildConfigJar,
                                        buildReleasePackage,
                                        buildPatch,
                                        useWorkingDirectory,
                                        configPrefixes,
                                        databaseChangePrefixes,
                                        javaHome,
                                        patchFile,
                                        m2SettingsXml,
                                        DefaultCliGetArtifactInfo.getArtifactInfo(interactive))) {
                                cliBuildTool.startBuildEnvironment();
                                cliBuildTool.build(fetch).start();
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
