# How to use this tool

## Setup om files for each project

- Set up a project pom for each specific company and artifact that you want to build. The
pom for will be used to calculate the classpath, and build the artifact

- Here is a sample template for build you can modify your self

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
</project>
```

## Step to run the tool

- Download java 11
- [Download the tool](https://gitlab.tx-tech.com/internal-vn-tool/release-builder/-/releases)
- Setup bash alias for easy invocation of the tool:
```bash
echo "alias java11=<path-to-your-java-11-binary>" >> ~/.bashrc 
```
- Compile this tool to get executable jar (Optional)
  - Run `mvn clean compile assembly:single` to build the tool
  - Run `java -jar <path-to-jar> -ui` for UI mode or `java -jar <path-to-jar> --help` for what flags can be used to run in cli mode
  - *(Optional)* Here is a example one-line command to build your package in cli mode for already cloned project
    ```bash
    java11 -jar ReleaseBuilder-0.0.1-SNAPSHOT-jar-with-dependencies.jar -fetch -updateSnapshot --clonedDir /tmp/buildSrc --artifactFolder /tmp/buildArtifact -buildReleasePackage --gitUser <your-git-user> --gitPassword <your-git-password> --m2SettingsXml <custom-setting-xml-if-not-default> --baseRef <startingRef> --targetRef <endRef>
    ```
- Run the tools in UI mode
```bash
java11 -jar ReleaseBuilder-0.0.1-SNAPSHOT-jar-with-dependencies.jar -ui
```

# How to build? (UI)

- Open local repo, then select your project

 ![step_1.png](images%2Fstep_1.png)

  It should look like this

  ![step_2.png](images%2Fstep_2.png)

- Switch your project to the desired target commit using your git tool of choice
- Press *Sync git view* to refresh the git tree UI
- Search your commit hash in the *Seach commit* bar
- Select your base commit, it will build from this base commit to your target commit
- Recheck all the diff entry

  ![step_3.png](images%2Fstep_3.png)

- *(Optional)* Filter out all unwanted changes using regex in *Filter entry regex* bar, focus out the bar for UI to update 
- Config your build
  - *Build patch*: will output java patch.jar only
  - *Build config*: will output all your config changes
  - *Build release package*: Combine patch + config + db change and package it to a zip file
  - *Maven settings xml file*: Change this if you use different name for your settings.xml
- Build. 

  ![step_4.png](images%2Fstep_4.png)

- If successfully built it should look like this

  ![step_5.png](images%2Fstep_5.png)

- Change your build artifact name, or accept default name, after that you can open your artifact folder

  ![step_6.png](images%2Fstep_6.png)

# How to debug the builder

By default, it will output to terminal, you can check what it doing there as well as exceptions will be logged to /tmp/error.log, you can check the log to know why the build is not successful.

## FAQ

- Where is my local change after running the tool?

It's in your git stash, you can view what it's save with `git stash show stash@{<your-stash-number, e.g: 0, 1,..>}`, to get your stash number you can retrace git operations with `git reflog`, you're expected to unstash any modification yourself after the build

- The tools report that it encounter merge conflicts when trying to checkout diffrent commit

Resolve the conflicts using your prefered tools. Then rerun the builder.

- I got error `can't calculate classpaths`

By default it will calculate the classpaths for your project using `mvn compile dependency:build-classpath`, make sure your the module can be compiled successfully with maven first. 

- I just want it to ignore some compilation errors

Create a git patch file with minimal modification for successful compilation, then specify it using `patchFile` flag (NOTE: Change in patch file will not be included in the final artifact)

- I encounter ClassNotFoundException

If you ignore the compilation errors maven will **TRY IT BEST** to compile any file it can while ignored files that it can't compile. So it could miss your change if your change have compilation error

- It's missing some files

For java files, if your change is outside the module specified in the project pom.xml then it's file will not be included when building the artifact. Make sure to fully include your modules. Also for config and database changes, prefixes will be used to filter out the change (e.g: config/, config_company/, DatabaseChange/), change that does not meet the conditions above will be ignored 

- It got git index.lock error

Because it crashes while not clean up the lock yet so it's not able to interact with git further until that lock is removed. Delete the file .git/index.lock then retry

- It has unwanted changes

You can filter out the changes then rebuild it again

- It contains more than xxxBuild artifacts in the popup

By default, it will collect all top-level module (module with no one else depend on it to determine the list of build artifacts), check your top-level pom and all your module pom as well as run

```bash
mvn clean compile dependency:build-classpath
```

to make sure that the erroneous artifact should exist in the classpath of some other module.



