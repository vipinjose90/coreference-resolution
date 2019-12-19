Team Name: GRAY FOXES
Team Members: Vipin Jose, Neha Pathapati 

-Please run “coreference.sh” to execute the program. Change the “listfile” and the “response_directory” arguments if required.


*****DESCRIPTION OF THE FILES**********

-There are 3 java files coded by us: WordNet.java, CoreferenceResolution.java and CoreferenceResolver.java

-The program uses the Stanford Core NLP package for POS tagging and Named Entity Recognition. The JAR files used for this is placed in the JAR folder. This is the external resource that is used. This package uses some classifiers internally which have been placed in the “classifiers” folder.
The program also uses pre-trained WordNet(for NER), word vectors, the input files for which are placed in the classifiers folder.

-The program took close to 7 minutes to run on the input files for init-eval combined.
Loading the word vectors primarily takes time. Remaining processing takes close to 10 seconds for each input file.

