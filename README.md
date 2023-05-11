# How to use this tool

## Setup pom files for each project

- Setup a project pom for each specific company and artifact that you want to build. The
pom for will be used to calculate the classpath, and build the artifact

- Here is a sample template for build you can modified your self

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ttl.company</groupId>
    <artifactId>TPS</artifactId>
    <packaging>pom</packaging>
    <version>0.0.1-SNAPSHOT</version>

    <!-- sub modules -->
    <modules>
        <module>CoreCommon</module>
        <module>CoreServer</module>
        <module>HKSCommon</module>
        <module>HKSFOCommon</module>
        <module>HKSFOServer</module>
        <module>InvestNetServer</module>
        <module>MDI</module>
        <module>TP</module>
        <module>VNServer</module>
        <module>WinVestStudio</module>
        <module>TPSServerBuild</module>
        <module>XSF</module>
        <module>CoreClient</module>
        <module>WVC</module>
        <module>InvestNetClient</module>
        <module>VNClient</module>
        <module>TPSClientBuild</module>
    </modules>
</project>%
```
## Building the project to get executable jar (Optional)

- Run `mvn clean compile assembly:single` to build the tool
- Run `java -jar <path-to-jar> -ui` for UI mode or `java -jar <path-to-jar> --help` for what flags can be used to run in cli mode
- Here is a example command to build your package using cli mode for already cloned project
```bash
java -jar ReleaseBuilder-0.0.1-SNAPSHOT-jar-with-dependencies.jar -fetch -updateSnapshot -mavenClean --clonedDir /tmp/buildSrc --artifactFolder /tmp/buildArtifact -buildReleasePackage --gitUser <your-git-user> --gitPassword <your-git-password> --m2SettingsXml <custom-setting-xml-if-not-default> --baseCommit <starting-commit> --targetCommit <end-commit>
```

# Internal working of the tools

When building the patch for the project, the tools will do the following

<ol>
  <li>Prepare your local repo for the build</li>
    <ol style="list-style-type: upper-alpha">
        <li>Save a reference to your current HEAD</li>
        <li>Stash any modifications (including untracked files and staging indexes)</li>
        <li>Checkout target commit</li>
    </ol>
  <li>Copy the company-specific project pom to the repo</li>
  <li>Use it to build the classfile and classpath. NOTE: if you want to ignore compilation errors of submodules. Refer to the <a href="#faq">FAQ</a></li>
  <li>Calculate the diff between target commit and base commit, and <u><b>FILTER OUT</b></u> any  changes outside the module defined in the project-company pom</li>
  <li>Base on the diff, it will locate the class location using the calculated classpaths</li>
  <li>Copy it to the classes folder inside the artifact folder</li>
  <li>Zip the classes folder to create the ${artifactId}.jar</li>
  <li>Checkout your previous HEAD </li>
</ol>

# How to debug the builder

By default, exceptions will be logged to /tmp/error.log, you can check the log to know why the build is not successful. You can also see what's it doing on console output to get more context

## FAQ

- Where is my local change after running the tool?

It's in your git stash, you can view what it's save with `git stash show stash@{<your-stash-number, e.g: 0, 1,..>}`, to get your stash number you can retrace git operations with `git reflog`, you're expected to unstash any modification after the build

- The tools report that it encounter merge conflicts when trying to checkout diffrent commit

Resolve the conflicts using your prefered tools. Then rerun the builder.

- I got error `can't calculate classpaths`

By default it will calculate the classpaths for your project using `mvn compile dependency:build-classpath`, make sure your the module can be compiled successfully with maven first. 

- I just want it to ignore some compilation errors

Add this section to your submodule pom.xml, create a new local commit, then build the artifact using the base commit and your new local commit as target commit

```xml
    <build>
        <plugins>
            <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <failOnError>false</failOnError>
            </configuration>
        </plugin>
    </plugins>
  </build>
```

- I encounter ClassNotFoundException

If you ignore the compilation errors maven will **TRY IT BEST** to compile any file it can while ignored files that it can't compile. So it could miss your change if your change have compilation error

- It's missing some files

For java files, if your change is outside the module specified in the project pom.xml then it's file will not be included when building the artifact. Make sure to fully include your modules. Also for config and database changes, prefixes will be used to filter out the change (e.g: config/, config_company/, DatabaseChange/), change that does not meet the conditions above will be ignored 

- It got git index.lock error

Because it crash while not clean up the lock yet so it's not able to interact with git until that lock is removed. Delete the file .git/index.lock then retry
