/**
 *
 * @author Vipin Jose, Neha
 */

import java.io.*;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class CoreferenceResolution {

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     * @throws java.lang.ClassNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParserConfigurationException, SAXException, ClassNotFoundException, Exception {
        
        WordNet wn = new WordNet();
        if (args.length<2){
            System.out.println("Please specify 2 arguments: LISTFILE and RESPONSE DIRECTORY!");
            System.exit(1);
        }
        else{
            Scanner inFiles = new Scanner (new File (args[0]));
            String str;
            while (inFiles.hasNextLine()){
                str=inFiles.nextLine();
                if(str!=null && !str.equalsIgnoreCase("") && !str.equalsIgnoreCase("\\s+")){
                    CoreferenceResolver cr= new CoreferenceResolver(str,args[1]);
                    cr.startCoreference();
                }
            }
        }
    }   
}

