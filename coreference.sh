cd CoreferenceResolutionv2
javac -cp :JAR/stanford-corenlp-full-2015-12-09/*:JAR/stanford-parser-full-2015-12-09/*:.:  *.java
java -cp :JAR/stanford-corenlp-full-2015-12-09/*:JAR/stanford-parser-full-2015-12-09/*:.: CoreferenceResolution inputfiles.txt Outputs/