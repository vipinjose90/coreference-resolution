
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;


/**
 *
 * @author vipinjose
 */
public class WordNet {
    
    static ArrayList<HashSet> wordList = new ArrayList<>();
    static HashMap <String, float[]> wordVec= new HashMap<>(); 
    
            Scanner fileName,fileName2;

    public WordNet() throws FileNotFoundException, IOException {
        this.fileName = new Scanner (new File ("classifiers/wordnetwords.txt"));
    //    this.fileName2 = new Scanner (new File ("classifiers/glove.6B/glove.6B.300d.txt"));
    
        String [] vecFiles= {   
                                "classifiers/glove.twitter.27B/glove.twitter.27B.200d.txt",
                                "classifiers/glove.6B/glove.6B.300d.txt"    
                            };

    
//        String [] vecFiles= {   
//                                 "classifiers/glove.twitter.27B/glove.twitter.27B.200d.txt"
//                            };  
    
        while(fileName.hasNextLine()){
            ArrayList<String> level=new ArrayList<>();
            String line=fileName.nextLine();
            String [] lineSplit=line.split("\\]\\s+\\[");
            String [] secondSplit=lineSplit[1].split("]");
            level.add(secondSplit[0].toLowerCase().trim());
            if(secondSplit.length>1){
                String [] rightWords=secondSplit[1].split(",");
                for(int i=0;i<rightWords.length;i++){
                    String [] splitClause=rightWords[i].split("\\s+");
                    level.add(splitClause[splitClause.length-1].toLowerCase().trim());
                }
            }
            HashSet <String> similar = new HashSet<>();
            for(int i=0;i<level.size();i++){
                similar.add(level.get(i));
            }
            wordList.add(similar);
        }
        fileName.close();
        
        
        for(int k=0;k<vecFiles.length;k++){
            fileName2=new Scanner (new File (vecFiles[k]));
            System.out.println("Loading Word Vector file: "+vecFiles[k]);
            while(fileName2.hasNextLine()){
                try{
                    String [] vecSplit = fileName2.nextLine().split("\\s+",2);
                    String [] vecValStr = vecSplit[1].split("\\s+");
                    float []  vecVals = new float[vecValStr.length];
                    for(int i=0;i<vecValStr.length;i++){
                        vecVals[i]=Float.parseFloat(vecValStr[i]);
                    }
                    
//                    if(vecSplit[0].contains("_")){
//                        String [] part1Split=vecSplit[0].split("_");
//                        wordVec.put(part1Split[part1Split.length-1].toLowerCase().trim(), vecVals);
//                    }
//                    else
                        wordVec.put(vecSplit[0].toLowerCase().trim(), vecVals);
                }
                catch(Exception e){
                    continue;
                }
            }
            fileName2.close();
        }
    }
}
