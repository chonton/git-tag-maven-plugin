# git-tag-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable version already exists.

Mojo details at [plugin info](https://chonton.github.io/git-tag-maven-plugin/0.0.1-SNAPSHOT/plugin-info.html)

One goal: [tag](https://chonton.github.io/git-tag-maven-plugin/0.0.1-SNAPSHOT/tag.html) creates an
annotated tag and pushes to the remote repository.

| Parameter | Required | Property | Default | Description |
|-----------|----------|----------|---------|-------------|
|skip       | No       |git.skip  |         |Skip executing the plugin |
|tag        | Yes      |git.tag   |         |Tag name     |
|message    | No       |git.message|release ${tag}|Tag message|
|branch     | No       |git.branch|HEAD     |Tag at head of this branch|
|remote     | No       |git.remote|origin   |Remote to push tag to|
|skipPush   | No       |git.skipPush|       |Skip pushing the tag to remote|

Typical use:

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>git-tag-maven-plugin</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <executions>
          <execution>
            <goals>
              <goal>tag</goal>
            </goals>
            <configuration>
          
            </configuration>
          </execution>
        </executions>
      </plugin>

    </plugins>
  </build>
```
