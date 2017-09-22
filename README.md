# git-tag-maven-plugin

Tag the current head of the local git repository with a tagName and message.  This annotated tag is
then pushed to a remote repository.  The push can optionally be disabled.

The credentials used to authenticate to the remote repository are extracted from the
[servers](https://maven.apache.org/settings.html#Servers) section of the maven settings.xml.  The
particular server credentials are selected by matching the host portion of the git url with a server id.
e.g. ```git@github.com:chonton/git-tag-maven-plugin.git``` or ```https://github.com/chonton/git-tag-maven-plugin.git```,
will match a server section with the id ```github.com```.  The credentials type must correlate with the
provided credentials; for an ssh git url, the server should include a &lt;privateKey> and &lt;passphrase>.
For an https git url, the server should include a &lt;username> and &lt;password>.

If an ssh git url is used and you want to use standard ssh configuration from the ~/.ssh directory
instead of using ~/.m2/settings.xml, set the useDotSsh parameter to true. 

An alternate branch can be specified. An alternate remote repository can be specified.

Mojo details at [plugin info](https://chonton.github.io/git-tag-maven-plugin/0.0.2/plugin-info.html).

The [tag](https://chonton.github.io/git-tag-maven-plugin/0.0.2/tag-mojo.html) goal is by default
attached to the *deploy* phase.  This will occur after any packaging type's deploy plugin goal.

The supported parameters are:

| Parameter | Required | Property | Default | Description |
|-----------|----------|----------|---------|-------------|
|skip       | No       |git.skip  |false    |Skip executing the plugin |
|tagName    | Yes      |git.tagName|         |Tag name     |
|message    | No       |git.message|release ${tagName}|Tag message|
|branch     | No       |git.branch|HEAD     |Tag at head of this branch|
|remote     | No       |git.remote|origin   |Remote to push tag to|
|useDotSsh  | No       |git.use.ssh|false   |Use the contents of ~/.ssh instead of ~/.m2/settings.xml to configure ssh connections|
|skipPush   | No       |git.skipPush|false  |Skip pushing the tag to remote|

### Typical attached phase use:

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>git-tag-maven-plugin</artifactId>
        <version>0.0.2</version>
        <executions>
          <execution>
            <goals>
              <goal>tag</goal>
            </goals>
            <configuration>
              <tagName>${version}</tagName>
            </configuration>
          </execution>
        </executions>
      </plugin>

    </plugins>
  </build>
```

### Typical command line use:
```sh
mvn org.honton.chas:git-tag-maven-plugin:tag -Dgit.tagName=0.0.1
```
