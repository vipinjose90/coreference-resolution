import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class CoreferenceResolver {
    
    //AnaphoraData []var = new var[100]; 
    String inFile,outFold,outFile;
    boolean tagMiss=false,firstFlag=false;
    String tagMissLine="";
    int lineCount=0;
    Map <Integer,String> tagLine= new HashMap<>();
    Map <Integer,String> noTagLine= new HashMap<>();
    PrintWriter pw;
    ArrayList<AnaphoraDetails> anaphoras=new ArrayList<>();
    ArrayList<NPDetails> nps=new ArrayList<>();
    HashMap<String, String> namedEntities = new HashMap<>();
    int coRefCount=1;
    String lowestCoRef;
    int iterVal=-243;
    ArrayList<W2V> cosClass;

    
    class AnaphoraDetails{
        int lineNo;
        int pos;
        String anaphora;
        String headNoun;
        String coRefID;
        String hRef="";
    }
    
    class W2V{
        AnaphoraDetails and;
        NPDetails npd;
        double cosineSim;
        boolean andFlag=false;
        boolean npdFlag=false;
    }
    
    class NPDetails{
        int lineNo;
        int pos;
        String nounPhrase;
        String headNoun;
        String coRefID="";
        String mapped="";
    }
    
    CoreferenceResolver(String inFile, String outFold ) throws FileNotFoundException{
        this.inFile=inFile;
        this.outFold=outFold;  
    }
    
    public void startCoreference() throws FileNotFoundException, IOException, ParserConfigurationException, SAXException, ClassCastException, ClassNotFoundException, Exception{

        String [] inArray=inFile.split("/");
        String [] inArray2=inArray[inArray.length-1].split("\\.");
        outFile=inArray2[inArray.length-2];
        String outputFile=outFold+"/"+outFile+".response";
        try{
            pw=new PrintWriter(outFold+"/"+outFile+".response");
        }
        catch(Exception c){
            System.out.println("ERROR!!");
            System.out.println("Unable to create the output file: "+outFile);
            System.exit(0);
        }
        System.out.println("\n\nInput File: "+inFile);
        System.out.println("Output File: "+outputFile);
        
        createHashMaps();
        createNPDetails();
        addAnaphoraPositions();
        getNamedEntities();
        resolveAnaphoraMatches();
        resolveCosineMatches();
        resolveNPMatches1();
        resolveNPMatches2(); 
        resolveWordNetMatches();
        resolveNamedEntityMatches();
        resolveMultiWordMatches();
        resolveAppositives();
        givePreviousReferences();
        createOutput();
        postProcessing();
        printOutput();
//      printStructures();
    }
    
    void printStructures(){

        System.out.println("\n\n\n*********Anaphoras********");
        for(AnaphoraDetails ad:anaphoras){
            System.out.println(ad.lineNo+"\t"+ad.pos+"\t"+ad.coRefID+"\t"+ad.hRef+"\t"+ad.anaphora+"\t"+ad.headNoun);
        }
        System.out.println("\n\n\n*********NPs********");
        for(NPDetails npd:nps){
            System.out.println(npd.lineNo+"\t"+npd.pos+"\t"+npd.coRefID+"\t"+"\t"+npd.nounPhrase+"\t"+npd.headNoun);
        }
    }
    
    
    public void createHashMaps() throws FileNotFoundException, ParserConfigurationException, SAXException, IOException{
        Scanner reader=null;
        try{
            reader = new Scanner (new File (inFile));
        }
        catch(Exception c){
            System.out.println("ERROR!!");
            System.out.println("Error opening input file: "+inFile);
            System.exit(0);
        }
        while(reader.hasNextLine()){
            String line=reader.nextLine();
       //     tagLine.put(lineCount,line);
            if (!line.trim().equals("<TXT>") && !line.trim().equals("</TXT>")){
                preProcessLine(line, lineCount);
                lineCount++;  
            }
        }
    }
    
    public void preProcessLine(String line, int linenum) throws ParserConfigurationException, SAXException, IOException {
        try{
            
            if(tagMiss==true){
                line=tagMissLine+" "+line;
                tagMiss=false;
            }
            
            String linemod="<THISTART>"+line+"</THISTART>";
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(linemod));
            Document document = db.parse(is);
            NodeList nodeList = document.getElementsByTagName("COREF");
        

            for(int y = 0,size= nodeList.getLength(); y<size; y++) {
                String anaphora = nodeList.item(y).getTextContent();
                String corefID = (nodeList.item(y).getAttributes().getNamedItem("ID").getNodeValue());
                AnaphoraDetails tempClass=new AnaphoraDetails();
                tempClass.anaphora=anaphora;
                tempClass.lineNo=linenum;
                tempClass.coRefID=(corefID);
                String[] wordsOfAnaphora = anaphora.trim().split("\\s+");
                tempClass.headNoun = wordsOfAnaphora[wordsOfAnaphora.length-1].trim().toLowerCase();
                anaphoras.add(tempClass);
                if(firstFlag==false){
                    firstFlag=true;
                    lowestCoRef=(corefID);
                }
            }
            tagLine.put(linenum, line);
            String noHTMLString = line.replaceAll("\\<COREF.*?>|</COREF>","");
            noTagLine.put(linenum,noHTMLString); 

        }
        catch(Exception e){ 
            tagMiss=true;
            tagMissLine=line;
        } 
        
    }


    
    public void getNamedEntities() throws Exception {

        String serializedClassifier = "classifiers/english.all.3class.distsim.crf.ser.gz";

        AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);

        String check="";
        String str="";
        
        for (Map.Entry<Integer, String> entry : noTagLine.entrySet()) {
            Integer lineCount = entry.getKey();
            String lineWithoutTags = entry.getValue();

            if(!lineWithoutTags.isEmpty()) {
                str=str+lineWithoutTags;  
            }
        }

        
        String [] splitLines;
 
        check=check+classifier.classifyToString(str, "tsv", false);

        
        splitLines=check.split("\n");
        for(int i=0;i<splitLines.length;i++){
            String [] splitWords=splitLines[i].split("\\s+");
            if(splitWords.length==2){
                if (!splitWords[1].equalsIgnoreCase("O")){
                    namedEntities.put(splitWords[0].toLowerCase().trim(), splitWords[1].toLowerCase().trim());
                }
            }
        }
        
//        for(String s:namedEntities.keySet()){
//            System.out.println(s+"\t"+namedEntities.get(s));
//        }
    }

    public void parser(String text,int line) {
        
        String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

        LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);

        String sent2 = text;
        TokenizerFactory<CoreLabel> tokenizerFactory =
        PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
        Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(sent2));
        List<CoreLabel> rawWords2 = tok.tokenize();
        Tree parse = lp.apply(rawWords2);

        TreebankLanguagePack tlp = lp.treebankLanguagePack(); // PennTreebankLanguagePack for English
        GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
        GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
        List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();

        List<Tree> leaves = parse.getLeaves();

        
        List l = null;
        List<StringBuilder> NPsForEachLine = new ArrayList<>();
        Tree abc;
        for(Tree parse1:parse){
            if(parse1.label().value().equals("NP")){  
                l = parse1.getLeaves();
                String y = "";              
                for(int j=0; j<l.size(); j++) {    
                    y=y+" "+l.get(j).toString().toLowerCase().trim();
                }              
                NPDetails np=new NPDetails();
                np.lineNo=line;
                np.nounPhrase=y.toLowerCase();
                int tempPos=parse.leftCharEdge(parse1);
                String tempString=y.replaceAll("\\s+","");
                
                String[] npTemp = y.trim().split("\\s+");
                np.headNoun = npTemp[npTemp.length-1].trim().toLowerCase();
                if ((np.headNoun.equalsIgnoreCase("'s"))||("/*!@#$^&*()\"{}_[]|\\?/<>,.".contains(np.headNoun)))
                    np.headNoun = npTemp[npTemp.length-2].trim().toLowerCase();
                if (np.headNoun.equalsIgnoreCase("%"))
                    np.headNoun = npTemp[npTemp.length-2].trim().toLowerCase()+npTemp[npTemp.length-1].trim().toLowerCase();
                np.pos=tempPos+tempString.indexOf(np.headNoun);
                nps.add(np);
            }
        }
    }

    public void createNPDetails() throws Exception {

        for (Map.Entry<Integer, String> entry : noTagLine.entrySet()) {
            Integer lineCount = entry.getKey();
            String lineWithoutTags = entry.getValue();
            
            if(!lineWithoutTags.isEmpty()) {
                parser(lineWithoutTags,lineCount);
            }
        }
    }
    
    void addAnaphoraPositions(){
        for(AnaphoraDetails ad:anaphoras){
            String withTags=tagLine.get(ad.lineNo);
            String str;
            int position=0;

            str =withTags.replaceFirst("<COREF ID=\""+ad.coRefID+"\">?","!!&&%%!!&&");
            str = str.replaceAll("\\<COREF.*?>|</COREF>|\\s+","");

            position=str.indexOf("!!&&%%!!&&");
            String tempString=ad.anaphora.toLowerCase().replaceAll("\\s+", "");
            int headPos=tempString.indexOf(ad.headNoun);
            ad.pos=position+headPos;  
        }
    }
    
    void resolveAnaphoraMatches(){
        for(AnaphoraDetails ad:anaphoras){
            if(ad.hRef.equalsIgnoreCase("")){
                for(AnaphoraDetails adIter:anaphoras){
                    if((((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo)||(ad.lineNo>adIter.lineNo)) && (ad.headNoun.equalsIgnoreCase(adIter.headNoun))) && !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                        ad.hRef=adIter.coRefID;
                    }
                }
            }
        }
    }
    
    void resolveNPMatches1(){
        for(AnaphoraDetails ad:anaphoras){            
            if(ad.hRef.equalsIgnoreCase("")){
                for(NPDetails np:nps){
                    if((((ad.pos>np.pos && ad.lineNo==np.lineNo)||(ad.lineNo>np.lineNo))) && ad.headNoun.equalsIgnoreCase(np.headNoun)){                   
                        if((np.coRefID.equalsIgnoreCase("")&&(ad.hRef.equalsIgnoreCase(""))) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                            np.coRefID="X"+coRefCount;
                            coRefCount++;
                            ad.hRef=np.coRefID;
                            break;
                        }
                        if(((!np.coRefID.equalsIgnoreCase(""))&&(ad.hRef.equalsIgnoreCase(""))) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                            ad.hRef=np.coRefID;
                            break;
                        }                      
                    }
                }
            }
        }
    }
    
    void resolveNPMatches2(){
        for(AnaphoraDetails ad:anaphoras){            
            if(ad.hRef.equalsIgnoreCase("")){
                for(NPDetails np:nps){
                    if((((ad.pos>np.pos && ad.lineNo==np.lineNo)||(ad.lineNo>np.lineNo))) && ad.headNoun.equalsIgnoreCase(np.headNoun)){
                        for(AnaphoraDetails ann:anaphoras){
                            if((ann.anaphora.toLowerCase().contains(np.headNoun) && ann.lineNo<=np.lineNo) && !(ad.coRefID.equalsIgnoreCase(ann.coRefID))){
                                ad.hRef=ann.coRefID;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    void givePreviousReferences(){
        
         for(AnaphoraDetails ad:anaphoras){
            String finalRef="";
            int difference=100000;
            if(ad.hRef.equalsIgnoreCase("")){

                for(AnaphoraDetails adIter:anaphoras){
                    if((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo) || (ad.lineNo > adIter.lineNo)){
                        if((Math.abs(ad.pos-adIter.pos+ad.lineNo-adIter.lineNo)<difference)&& !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                            difference=Math.abs(ad.pos-adIter.pos+ad.lineNo-adIter.lineNo);
                            finalRef=adIter.coRefID;
                        }
                    }
                }
                ad.hRef=finalRef;
            }
            
            if(ad.hRef.equalsIgnoreCase("")){
                for(NPDetails np:nps){
                    if( ad.lineNo > np.lineNo){
                        if((Math.abs(ad.pos-np.pos+ad.lineNo-np.lineNo)<difference && !np.coRefID.equalsIgnoreCase("")) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                            difference=Math.abs(ad.pos-np.pos+ad.lineNo-np.lineNo);
                            finalRef=np.coRefID;
                        }
                    }
                }
                if ((finalRef.equalsIgnoreCase("")) && !(ad.coRefID.equalsIgnoreCase(lowestCoRef))){
                    ad.hRef=lowestCoRef;
                }
                else
                    if(!(ad.coRefID.equalsIgnoreCase(finalRef)))
                        ad.hRef=finalRef;       
            }
        }
        
    }
    
    int getReplacementPosition(NPDetails np, int posit){
        String actualStr=tagLine.get(np.lineNo);
        String tStr,firstPart,middlePart,lastPart;
        String tTagLine=actualStr.toLowerCase();
        tStr=np.headNoun.toLowerCase();
        int ind=tTagLine.indexOf(tStr,posit);
        if (ind<0)
            return -1;
        if (ind==0)
            return 0;
        if (iterVal==-243){
            iterVal=ind;
        }
        firstPart=actualStr.substring(0, ind-1);
        middlePart=actualStr.substring(ind, ind+np.headNoun.length());
        lastPart=actualStr.substring(np.headNoun.length(),actualStr.length()-1);
        if((firstPart.lastIndexOf("<COREF ID")) > (firstPart.lastIndexOf("</COREF>"))){
            return getReplacementPosition(np,posit+np.headNoun.length());
        }
        else{
            return ind;
        }
    }
    
    void resolveNamedEntityMatches(){
        for(AnaphoraDetails ad:anaphoras){
            if(ad.hRef.equalsIgnoreCase("")){
                for(AnaphoraDetails adIter:anaphoras){
                    if(((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo)||(ad.lineNo>adIter.lineNo)) && (namedEntities.containsKey(ad.headNoun.toLowerCase())) && ((namedEntities.containsKey(adIter.headNoun.toLowerCase())))){
                        if(namedEntities.get(ad.headNoun).equalsIgnoreCase(namedEntities.get(adIter.headNoun)) && !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                            ad.hRef=adIter.coRefID;
                            break;
                        }
                    }
                }
            }
            
            if(ad.hRef.equalsIgnoreCase("")){
                for(NPDetails np:nps){
                    if((((ad.pos>np.pos && ad.lineNo==np.lineNo)||(ad.lineNo>np.lineNo))) && (namedEntities.containsKey(ad.headNoun.toLowerCase())) && ((namedEntities.containsKey(np.headNoun.toLowerCase())))){
                        for(AnaphoraDetails ann:anaphoras){
                            if((namedEntities.containsKey(ann.headNoun.toLowerCase())) && (namedEntities.containsKey(np.headNoun.toLowerCase())) && (ann.lineNo<=ad.lineNo)){
                                if((namedEntities.get(ann.headNoun).equalsIgnoreCase(namedEntities.get(np.headNoun))) && !(ad.coRefID.equalsIgnoreCase(ann.coRefID))){
                                    ad.hRef=ann.coRefID;
                                    break;
                                }
                            }
                        }
                        if(((np.coRefID.equalsIgnoreCase("")) && (ad.hRef.equalsIgnoreCase("")) && (namedEntities.get(np.headNoun).equalsIgnoreCase(namedEntities.get(ad.headNoun))))&& !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                            np.coRefID="X"+coRefCount;
                            coRefCount++;
                            ad.hRef=np.coRefID;
                            break;
                        }    
                    }
                }
            }
        }
    }
    
    void createOutput(){
        
        for(AnaphoraDetails ad:anaphoras){
            if(!ad.hRef.equals("")){
                String addRef;
                addRef=tagLine.get(ad.lineNo).replaceFirst("<COREF ID=\""+ad.coRefID+"\">?", "<COREF ID=\""+ad.coRefID+"\" REF=\""+ad.hRef+"\">");
                tagLine.put(ad.lineNo, addRef);
            }           
        }
        
        for(NPDetails np:nps){
            if(!np.coRefID.equals("")){
                iterVal=-243;
                String actualStr=tagLine.get(np.lineNo);
                String firstPart="",middlePart="",lastPart="",finalString="";
                int ind=getReplacementPosition(np,np.pos);               
                if(ind<0){
                    np.coRefID="";
                }
                if(actualStr.length()==ind+np.headNoun.length()){
                    firstPart=actualStr.substring(0, ind-1);
                    middlePart=actualStr.substring(ind, ind+np.headNoun.length());         
                    middlePart=" <COREF ID=\""+np.coRefID+"\">"+middlePart+"</COREF>";
                    finalString=firstPart+middlePart;
                    tagLine.put(np.lineNo, finalString);
                    
                }
                else{

                    if(ind>0){
                        firstPart=actualStr.substring(0, ind-1);
                        middlePart=actualStr.substring(ind, ind+np.headNoun.length());
                        lastPart=actualStr.substring(ind+np.headNoun.length(),actualStr.length());          
                        middlePart=" <COREF ID=\""+np.coRefID+"\">"+middlePart+"</COREF>";
                        finalString=firstPart+middlePart+lastPart;
                        tagLine.put(np.lineNo, finalString);

                    }
                    if (ind==0){
                        
                        middlePart=actualStr.substring(0, ind+np.headNoun.length());
                        lastPart=actualStr.substring(ind+np.headNoun.length(),actualStr.length());
                        middlePart=" <COREF ID=\""+np.coRefID+"\">"+middlePart+"</COREF>";
                        finalString=middlePart+lastPart;
                        tagLine.put(np.lineNo, finalString);  
                    }
                }
            }            
        }  
    }
    
    public void printOutput(){
        pw.println("<TXT>");
        pw.flush();
        for(int i=0;i<lineCount;i++){
            if(tagLine.get(i)!=null){
                pw.println(tagLine.get(i));
                pw.flush();
            }
        }
        pw.println("</TXT>");
        pw.flush();
    } 
    
    
    void postProcessing() throws ParserConfigurationException, SAXException, IOException{
        ArrayList <AnaphoraDetails> checkTags=new ArrayList<>();
        HashSet <String> coRefCheck = new HashSet<>();
        HashSet <String> refCheck = new HashSet<>();
        for(int i=0;i<lineCount;i++){
            String line;
            if((line=tagLine.get(i))!=null){
                int linenum=i;
                String linemod="<THISTART>"+line+"</THISTART>";
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                InputSource is = new InputSource(new StringReader(linemod));
                Document document = db.parse(is);
                NodeList nodeList = document.getElementsByTagName("COREF");


                for(int y = 0,size= nodeList.getLength(); y<size; y++) {
                    String refID;
                    String anaphora = nodeList.item(y).getTextContent();
                    String coRefID = (nodeList.item(y).getAttributes().getNamedItem("ID").getNodeValue());

                    if((nodeList.item(y).getAttributes().getNamedItem("REF"))!=null){
                        refID = (nodeList.item(y).getAttributes().getNamedItem("REF").getNodeValue());
                    }
                    else
                        refID="";
                    AnaphoraDetails tempClass=new AnaphoraDetails();
                    tempClass.anaphora=anaphora;
                    tempClass.coRefID=(coRefID);
                    tempClass.hRef=refID;
                    tempClass.pos=0;
                    coRefCheck.add(coRefID);
                    refCheck.add(refID);
                    checkTags.add(tempClass);
                }
            }
        }
        for (AnaphoraDetails checkTag : checkTags) {
            if((!coRefCheck.contains(checkTag.hRef) && (!checkTag.hRef.equals("")))){
                checkTag.pos=-1;
            }
            if(checkTag.coRefID.equals(checkTag.hRef)){
                checkTag.pos=-2;
            }
        }
        
        for (AnaphoraDetails checkTag : checkTags) {
            if(checkTag.pos<0){
                for(int i=0;i<lineCount;i++){
                    String line;
                    if((line=tagLine.get(i))!=null){
                        String newTag="";
                        for (AnaphoraDetails checkTag2 : checkTags){
                            if(!(checkTag2.coRefID.equals(checkTag.coRefID))){
                                newTag=checkTag2.coRefID;
                                break;
                            }
                        }
                        String proLine = line.replaceAll("<COREF ID=\""+checkTag.coRefID+"\" REF=\""+checkTag.hRef+"\">","<COREF ID=\""+checkTag.coRefID+"\" REF=\""+newTag+"\">");
                        tagLine.put(i,proLine);
                    }        

                }
            }
        }
    }
    
    void resolveWordNetMatches(){
        for(AnaphoraDetails ad:anaphoras){
            if(ad.hRef.equalsIgnoreCase("")){
                nestedLoop:
                for(AnaphoraDetails adIter:anaphoras){
                    if(((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo)||(ad.lineNo>adIter.lineNo)) && !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                        for(int i=0;i<WordNet.wordList.size();i++){
                            if(WordNet.wordList.get(i).contains(ad.headNoun.toLowerCase()) && WordNet.wordList.get(i).contains(adIter.headNoun.toLowerCase())){
                                ad.hRef=adIter.coRefID;
                                break nestedLoop;
                            }
                        }
                    }
                }
            }
 
            if(ad.hRef.equalsIgnoreCase("")){
                nestedLoop2:
                for(NPDetails np:nps){
                    if((ad.pos>np.pos && ad.lineNo==np.lineNo)||(ad.lineNo>np.lineNo)){
                        for(int i=0;i<WordNet.wordList.size();i++){
                            if(WordNet.wordList.get(i).contains(ad.headNoun.toLowerCase()) && WordNet.wordList.get(i).contains(np.headNoun.toLowerCase())){
                                if((np.coRefID.equalsIgnoreCase("")) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                                    np.coRefID="X"+coRefCount;
                                    coRefCount++;
                                    ad.hRef=np.coRefID;
                                    break nestedLoop2;
                                }
                                if((!np.coRefID.equalsIgnoreCase("")) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                                    ad.hRef=np.coRefID;
                                    break nestedLoop2;
                                } 
                            }
                        }
                    }
                }
            }
        }
    }
    
    void resolveCosineMatches(){
        for(AnaphoraDetails ad:anaphoras){
            cosClass = new ArrayList();
            if((ad.hRef.equalsIgnoreCase("")) && WordNet.wordVec.containsKey(ad.headNoun)){
                float[] adVal = WordNet.wordVec.get(ad.headNoun);
                int vecSize=adVal.length;
                for(AnaphoraDetails adIter:anaphoras){
                    if(((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo)||(ad.lineNo>adIter.lineNo)) && !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                        if(WordNet.wordVec.containsKey(adIter.headNoun)){
                            float[] ancVal;
                            try{
                                ancVal = WordNet.wordVec.get(adIter.headNoun);
                            }
                            catch(Exception e){
                                continue;
                            }
                            if(ancVal.length==adVal.length){
                                double dnum1=0, dnum2=0, num=0;
                                for(int i=0;i<vecSize;i++){
                                    num= num+(adVal[i]*ancVal[i]);
                                    dnum1=dnum1+(adVal[i]*adVal[i]);
                                    dnum2=dnum2+(ancVal[i]*ancVal[i]);
                                }
                                dnum1=Math.sqrt(dnum1);
                                dnum2=Math.sqrt(dnum2);
                                W2V tempClass=new W2V();
                                tempClass.andFlag=true;
                                tempClass.and=adIter;
                                tempClass.cosineSim=num/(dnum1*dnum2);
                                cosClass.add(tempClass);
                            }
                        }
                    }
                }

                for(NPDetails np:nps){
                    if((ad.pos>np.pos && ad.lineNo==np.lineNo)||(ad.lineNo>np.lineNo) && !(ad.coRefID.equalsIgnoreCase(np.coRefID))){
                        if(WordNet.wordVec.containsKey(np.headNoun)){
                            float[] ancVal;
                            try{
                                ancVal = WordNet.wordVec.get(np.headNoun);
                            }
                            catch(Exception e){
                                continue;
                            }
                            if(ancVal.length==adVal.length){
                                double dnum1=0, dnum2=0, num=0;
                                for(int i=0;i<vecSize;i++){
                                    num= num+(adVal[i]*ancVal[i]);
                                    dnum1=dnum1+(adVal[i]*adVal[i]);
                                    dnum2=dnum2+(ancVal[i]*ancVal[i]);
                                }
                                dnum1=Math.sqrt(dnum1);
                                dnum2=Math.sqrt(dnum2);
                                W2V tempClass=new W2V();
                                tempClass.npdFlag=true;
                                tempClass.npd=np;
                                tempClass.cosineSim=num/(dnum1*dnum2);
                                cosClass.add(tempClass);
                            }
                        }

                    }
                }
            }
            W2V word=new W2V();
            double cosFin=-10000;
            for(int i=0;i<cosClass.size();i++){
                if(cosClass.get(i).cosineSim>cosFin){
                    cosFin=cosClass.get(i).cosineSim;
                    word=cosClass.get(i);
            //        System.out.println("Working:"+cosFin+" "+ad.headNoun);
                }
            }
            if(cosFin!=-10000){
                if(word.andFlag){
                    ad.hRef=word.and.coRefID;
                }
                else{
                    if(word.npd.coRefID.equalsIgnoreCase("")){
                        word.npd.coRefID="X"+coRefCount;
                        coRefCount++;
                        ad.hRef=word.npd.coRefID;
                    }
                    else{
                        ad.hRef=word.npd.coRefID;
                    }
                }
            }
        }   
    }
    
    void resolveMultiWordMatches(){
        for(AnaphoraDetails ad:anaphoras){
            if(ad.hRef.equalsIgnoreCase("")){
                int currCount=0;
                AnaphoraDetails tempH=null;
                String [] split1=ad.anaphora.split("\\s+");
                for(AnaphoraDetails adIter:anaphoras){
                    int tempCount=0;
                    if((((ad.pos>adIter.pos && ad.lineNo==adIter.lineNo)||(ad.lineNo>adIter.lineNo))) && !(ad.coRefID.equalsIgnoreCase(adIter.coRefID))){
                        String [] split2=adIter.anaphora.split("\\s+");
                        for(int i=0;i<split1.length;i++){
                            for(int j=0;j<split2.length;j++){
                                if(split1[i].trim().equalsIgnoreCase(split2[j].trim())){
                                    tempCount++;
                                }
                            }
                        }
                        if(tempCount>currCount && tempCount>=1){
                            currCount=tempCount;
                            tempH=adIter;
                        }
                    }
                }
                if(currCount>0 && tempH !=null){
                    ad.hRef=tempH.coRefID;
                //    System.out.println("Picked!!!!!!!!!!!!!!!!!!!!");
                }
            }
        }
    }
    
    public void resolveAppositives() {  
        for(AnaphoraDetails ad:anaphoras) {
            String anaphoraPhrase = ad.anaphora.trim().toLowerCase();
            
            if(ad.hRef.equalsIgnoreCase("")) {
            
                for(NPDetails np:nps) {
                    String nounPhrase = np.nounPhrase.trim().toLowerCase();

                    if(nounPhrase.equals(anaphoraPhrase)) {

//                        System.out.println("\n"+"EQUALS:"+"\t"+nounPhrase+"\t"+anaphoraPhrase);
                        
                        int anaphoraLineNo = ad.lineNo;
                        int anaphoraPos = ad.pos;
                        
//                        System.out.println("ANAPHORA LINE NO:"+anaphoraLineNo);
//                        System.out.println("ANAPHORA POS:"+anaphoraPos);

                        ArrayList<NPDetails> npsInAnaphoraLine=new ArrayList<>();
                        ArrayList<Integer> npsInAnaphoraLinePos = new ArrayList<>();
                        for(NPDetails nounPh:nps) {
                            if(nounPh.lineNo==anaphoraLineNo && nounPh.pos<anaphoraPos) {
                                npsInAnaphoraLine.add(nounPh);
                                npsInAnaphoraLinePos.add(nounPh.pos);
                            }
                        }
                        
//                        System.out.println();
//                        System.out.println("NPs IN ANAPHORA LINE:");
//                        for(NPDetails d: npsInAnaphoraLine) {
//                            System.out.print(d.nounPhrase);
//                            System.out.print("\t"+d.lineNo);
//                            System.out.print("\t"+d.pos);
//                        }
//                        System.out.println();

                        int nounPhrasePosClosestToAnaphora = 1000;
                        if(npsInAnaphoraLinePos.size()!=0) {
                            Collections.sort(npsInAnaphoraLinePos, Collections.reverseOrder());
                            nounPhrasePosClosestToAnaphora = npsInAnaphoraLinePos.get(0);
                        }

                        String nounPhraseClosestToAnaphora = null;
                        if(nounPhrasePosClosestToAnaphora!=1000) {
                            for(NPDetails nounp:npsInAnaphoraLine) {
                                if(nounPhrasePosClosestToAnaphora==(nounp.pos)) {
                                    nounPhraseClosestToAnaphora = nounp.nounPhrase.trim().toLowerCase(); 
                                    break;
                                }
                            }
                        }
                        
//                        System.out.println("NOUN PHRASE CLOSEST TO ANAPHORA:"+nounPhraseClosestToAnaphora);
                        
                        int startIndexOfNounPhraseClosestToAnaphora = 0;
                        int endIndexOfNounPhraseClosestToAnaphora = 0;
                        
                        if(nounPhraseClosestToAnaphora!=null) {
                            startIndexOfNounPhraseClosestToAnaphora = nounPhrasePosClosestToAnaphora;
                            endIndexOfNounPhraseClosestToAnaphora = startIndexOfNounPhraseClosestToAnaphora + nounPhraseClosestToAnaphora.length();
                        }
                        
//                        System.out.println("START POS OF NP CLOSEST TO ANAPHORA:"+startIndexOfNounPhraseClosestToAnaphora);
//                        System.out.println("END POS OF NP CLOSEST TO ANAPHORA:"+endIndexOfNounPhraseClosestToAnaphora);

                        if(nounPhraseClosestToAnaphora!=null) {
                            String lineWithNPAndAP = noTagLine.get(anaphoraLineNo).trim().toLowerCase();
                            
 //                           System.out.println("LINE:"+lineWithNPAndAP);

                            Pattern p = Pattern.compile(Pattern.quote(nounPhraseClosestToAnaphora) + "(?s)(.*?)" + Pattern.quote(anaphoraPhrase));
                            Matcher m = p.matcher(lineWithNPAndAP);

                            String textInBetween="";
                            while (m.find()) {
                                textInBetween = m.group(1);
                            }

//                            System.out.println("TEXT IN BETWEEN:"+textInBetween);

                            if(textInBetween!=null && textInBetween.trim().equals(",")) {
                                //appositive
//                                System.out.println("Appositive:");
//                                System.out.println("TEXT IN BETWEEN:"+textInBetween);
                                
                                ArrayList<NPDetails> NPPartsOfClosestNP = new ArrayList<>();
 //                               System.out.println("SUBPARTS OF CLOSEST NP:");
                                for(NPDetails d:nps) {
                                    if(d.lineNo==anaphoraLineNo && d.pos>=startIndexOfNounPhraseClosestToAnaphora && d.pos<=endIndexOfNounPhraseClosestToAnaphora) {
                                        NPPartsOfClosestNP.add(d);
                                    }
                                }
                                
                                for(int k=0; k<NPPartsOfClosestNP.size(); k++) {
                                    System.out.println(NPPartsOfClosestNP.get(k).nounPhrase+"\t"
                                                       + NPPartsOfClosestNP.get(k).coRefID);
                                }
                                
                                for(int i=0; i<NPPartsOfClosestNP.size(); i++) {
                                    if(!(NPPartsOfClosestNP.get(i).coRefID).equalsIgnoreCase("")) {
//                                        System.out.println(NPPartsOfClosestNP.get(i).nounPhrase);
//                                        System.out.println(NPPartsOfClosestNP.get(i).coRefID);
                                        ad.hRef = NPPartsOfClosestNP.get(i).coRefID;
                                        break;
                                    }
                                }
                                
                                if(ad.hRef.equalsIgnoreCase("")) {
//                                    System.out.println("Still empty Coref!");
                                    for(NPDetails NPD:nps) {
                                        if((NPD.nounPhrase.trim().toLowerCase()).equals(nounPhraseClosestToAnaphora)) {
                                            System.out.println(NPD.nounPhrase);
                                            NPD.coRefID="X"+coRefCount;
//                                            System.out.println("NP Coref:"+NPD.coRefID);
                                            coRefCount++;
                                            ad.hRef=NPD.coRefID;
                                            break;
                                        }
                                    }
                                }
//                                System.out.println("ANAPHORA COREF ID:"+ad.hRef);
                            }
                        }
                    }
                }
            }
        }
    }
}

