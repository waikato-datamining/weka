# weka
Patched versions of Weka (one per branch), as used by various ADAMS projects.

## Deployment

* Update version in `pom.xml`

* Build and deploy artifacts
  
```bash
export MAVEN_OPTS="$MAVEN_OPTS -DskipTests=true"
mvn clean deploy
```
