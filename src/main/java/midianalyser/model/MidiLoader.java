package midianalyser.model;

import java.io.File;

import java.util.*;

import java.lang.Math;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Track;
import javax.sound.midi.InvalidMidiDataException;

import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.*;

import java.io.IOException;


import javafx.collections.ObservableList;
import javafx.collections.FXCollections;


//TODO checkout https://stackoverflow.com/questions/3850688/reading-midi-files-in-java

public class MidiLoader{
    public static final int NOTE_ON = 0x90;
    public static final int NOTE_OFF = 0x80;
    private File midiDirectory;
    private ArrayList<File> midiFiles;
    private Sequence sequence;
    private ArrayList<Integer> listOfTones;
    private ArrayList<Integer> listOfRhythms;
    private TreeMap<String, HashMap<Integer, Integer>> mapOfTrochees;
    private TreeMap<String, HashMap<Integer, Integer>> mapOfDactyles;
    private TreeMap<String, Integer> mapOfKeys;
    private TreeMap<String, Integer> mapOfTimeSigs;

    private ObservableList<String> filterTimeSig = FXCollections.observableArrayList();
    private ObservableList<String> filterKeySig = FXCollections.observableArrayList();
    private ObservableList<String> filterMajorSig = FXCollections.observableArrayList();


    public MidiLoader(ArrayList<Integer> listOfTones, ArrayList<Integer> listOfRhythms, TreeMap<String, HashMap<Integer, Integer>> mapOfTrochees, TreeMap<String, HashMap<Integer, Integer>> mapOfDactyles, TreeMap<String, Integer> mapOfKeys, TreeMap<String, Integer> mapOfTimeSigs){
        this.listOfTones = listOfTones;
        this.listOfRhythms = listOfRhythms;
        this.mapOfTrochees = mapOfTrochees;
        this.mapOfDactyles = mapOfDactyles;
        this.mapOfKeys = mapOfKeys;
        this.mapOfTimeSigs = mapOfTimeSigs;

    }

    public void setLists(ArrayList<Integer> listOfTones, ArrayList<Integer> listOfRhythms, TreeMap<String, HashMap<Integer, Integer>> mapOfTrochees, TreeMap<String, HashMap<Integer, Integer>> mapOfDactyles, TreeMap<String, Integer> mapOfKeys, TreeMap<String, Integer> mapOfTimeSigs){
        this.listOfTones = listOfTones;
        this.listOfRhythms = listOfRhythms;
        this.mapOfTrochees = mapOfTrochees;
        this.mapOfDactyles = mapOfDactyles;
        this.mapOfKeys = mapOfKeys;
        this.mapOfTimeSigs = mapOfTimeSigs;
    }

    public int setDirectory(File midiDirectory) throws IOException{
        int fileAmount = 0;

        this.midiDirectory = midiDirectory;
        midiFiles = new ArrayList<>(FileUtils.listFiles(midiDirectory,new RegexFileFilter("^(.*.mid)"),DirectoryFileFilter.DIRECTORY));
        for(File file : midiFiles){
            System.out.println(file.getName());
            fileAmount ++;
        }
        clearAnalytics();

        return fileAmount;

    }

    public void countAll(){
        for(File file : midiFiles){
            try{
                sequence = MidiSystem.getSequence(file);
            }catch(IOException | InvalidMidiDataException e){
                 System.out.println(e.getMessage());
                continue;
            }

            int trackNumber = 0;
            ArrayList<MidiEvent> metaMessages = new ArrayList();

            for (Track track :  sequence.getTracks()) {
                int tempo = 500000;
                int PPQ = sequence.getResolution();
                long currQuarterTick =0; // round down to nearest tick representing a quarter
                long PPQChangeTick =0; // The tick of the last played note.
                int keySig = 0;
                boolean majorKey = true;
                int timeSigNumerator = 4;
                int timeSigDenominator = 4;

                trackNumber++;
                for(MidiEvent me : metaMessages){
                    track.add(me);
                }

                ArrayList<MidiNote> simulNotes = new ArrayList<MidiNote>();
                ArrayList<MidiNote> quarter = new ArrayList<MidiNote>();
                ArrayList<MidiEvent> events = sortTrack(track);

                for (int i=0; i < events.size(); i++) {
                    MidiEvent event = events.get(i);
                    MidiMessage message = event.getMessage();
                    //System.out.print(" @" + event.getTick());

                    if (message instanceof ShortMessage) {
                        ShortMessage sm = (ShortMessage) message;
                        //System.out.println(sm.getCommand());
                        if (sm.getCommand() == NOTE_ON && sm.getData2() != 0) {
                            int key = sm.getData1();
                            MidiNote note = new MidiNote(key,event.getTick(),keySig);

                            simulNotes.add(note);
                            quarter.add(note);

                        } else if (sm.getCommand() == NOTE_OFF || sm.getCommand() == NOTE_ON && sm.getData2() == 0) {
                            int key = sm.getData1();

                            for(int n = 0; n < simulNotes.size(); n++){
                                if(simulNotes.get(n).note() == key){
                                    int lengthInTicks = (int) (event.getTick() - simulNotes.get(n).startTick());
                                    System.out.println("note: " + key);
                                    System.out.println("lengthInTicks: " + lengthInTicks + ". PPQ: " + PPQ);
                                    simulNotes.get(n).setLength(0);
                                    for(double l = 0; l < 32; l +=0.25){
                                        if(lengthInTicks <= (int) ((double) PPQ/ (double) l)-((double) PPQ/ 20.0)){
                                            simulNotes.get(n).setLength(l);
                                        }else{
                                            break;
                                        }
                                    }
                                    System.out.println("length" + simulNotes.get(n).length());
                                    simulNotes.remove(n);
                                }
                            }

                            if(filterTimeSig.isEmpty() || filterTimeSig.contains(timeSigNumerator+"/"+timeSigDenominator)){
                                if(filterKeySig.isEmpty() || filterKeySig.contains(keyToString(rootNote(keySig,majorKey)))){
                                    if(filterMajorSig.isEmpty() || (filterMajorSig.contains("major") &&  majorKey) || (filterMajorSig.contains("minor") &&  !majorKey)){

                                        if(events.get(i+1).getTick() >= currQuarterTick + PPQ ){
                                            currQuarterTick = events.get(i+1).getTick()-((events.get(i+1).getTick()-PPQChangeTick) % PPQ);
                                            checkQuarter(quarter, keySig, majorKey);
                                        }

                                    }
                                }
                            }

                        }
                    }else if(message instanceof MetaMessage) {

                        MetaMessage mm = (MetaMessage) message;
                        int type = mm.getType();

                        System.out.println(type);

                        if(type == MidiEventType.TRACKNAME.type()){

                        }else if(type == MidiEventType.END_OF_TRACK.type()){
                            if(filterTimeSig.isEmpty() || filterTimeSig.contains(timeSigNumerator+"/"+timeSigDenominator)){
                                if(filterKeySig.isEmpty() || filterKeySig.contains(keyToString(rootNote(keySig,majorKey)))){
                                    if(filterMajorSig.isEmpty() || (filterMajorSig.contains("major") &&  majorKey) || (filterMajorSig.contains("minor") &&  !majorKey)){
                                        checkQuarter(quarter, keySig, majorKey);
                                    }
                                }
                            }


                        }else if(type == MidiEventType.SET_TEMPO.type()){
                            int out = 0;
                            for(byte bt : mm.getData()){
                                out += bt;
                                out <<= 8;
                            }
                            tempo = out;

                        }else if(type == MidiEventType.TIME_SIGNATURE.type()){
                            timeSigNumerator = mm.getData()[0];
                            timeSigDenominator = (int) Math.pow(2,mm.getData()[1]);
                            PPQ = sequence.getResolution();
                            if(timeSigDenominator == 8) PPQ = (int) Math.round(PPQ* 1.5);
                            //System.out.println("timeSig :" +timeSigNumerator + "/" + timeSigDenominator);
                            PPQChangeTick = event.getTick();
                            currQuarterTick = event.getTick()-((event.getTick()-PPQChangeTick) % PPQ);
                            if(event.getTick()> 0){
                                if(filterTimeSig.isEmpty() || filterTimeSig.contains(timeSigNumerator+"/"+timeSigDenominator)){
                                    if(filterKeySig.isEmpty() || filterKeySig.contains(keyToString(rootNote(keySig,majorKey)))){
                                        if(filterMajorSig.isEmpty() || (filterMajorSig.contains("major") &&  majorKey) || (filterMajorSig.contains("minor") &&  !majorKey)){
                                            if(currQuarterTick != event.getTick()){
                                                checkQuarter(quarter, keySig, majorKey);
                                            }
                                        }
                                    }
                                }
                            }


                            if(! metaMessages.contains(event)){
                                if(filterTimeSig.isEmpty() || filterTimeSig.contains(timeSigNumerator+"/"+timeSigDenominator)){
                                    if(filterKeySig.isEmpty() || filterKeySig.contains(keyToString(rootNote(keySig,majorKey)))){
                                        if(filterMajorSig.isEmpty() || (filterMajorSig.contains("major") &&  majorKey) || (filterMajorSig.contains("minor") &&  !majorKey)){

                                            addTimeSig(timeSigNumerator, timeSigDenominator);

                                        }
                                    }
                                }

                            }

                        }else if(type == MidiEventType.KEY_SIGNATURE.type()){
                            int oldKeySig = keySig;
                            boolean oldMajorKey = majorKey;

                            keySig = mm.getData()[0];
                            if(mm.getData()[1] != 0) majorKey = false;
                            System.out.println("keySig :" +keySig + "major" + majorKey);
                            if(filterTimeSig.isEmpty() || filterTimeSig.contains(timeSigNumerator+"/"+timeSigDenominator)){
                                if(filterKeySig.isEmpty() || filterKeySig.contains(keyToString(rootNote(keySig,majorKey)))){
                                    if(filterMajorSig.isEmpty() || (filterMajorSig.contains("major") &&  majorKey) || (filterMajorSig.contains("minor") &&  !majorKey)){
                                        if(! metaMessages.contains(event)){
                                            addKeySig(keySig, majorKey);
                                        }
                                        if(currQuarterTick != event.getTick()){
                                            checkQuarter(quarter, oldKeySig, oldMajorKey);
                                        }

                                    }

                                }
                            }


                        }
                        metaMessages.add(event);

                    }
                }
            }
        }
    }

    public ArrayList<MidiEvent> sortTrack(Track track){
        ArrayList<MidiEvent> eventlist = new ArrayList();
        for (int i=0; i < track.size(); i++) {
            eventlist.add(track.get(i));
        }

        Collections.sort(eventlist, new SortEventByTick());
        return eventlist;
    }

    public void checkQuarter(ArrayList<MidiNote> quarter, int keySig, boolean majorKey){
        Collections.sort(quarter, new SortByStartTick());
        System.out.println("notes in quarter: " +quarter.size());
        rhythmCheck(quarter);
        if(quarter.size() ==2){
            TrochaicCheck(quarter, keySig, majorKey);
        }else if(quarter.size() ==3){
            DactylCheck(quarter, keySig, majorKey);
        }
        for(MidiNote note : quarter){
            int noteFromKey = keySigCheck(note.note(), keySig, majorKey);
            listOfTones.set(noteFromKey,listOfTones.get(noteFromKey)+1);
        }


        quarter.clear();


    }

    public void rhythmCheck(ArrayList<MidiNote> quarter){

        switch(quarter.size()){
            case 1:
                listOfRhythms.set(0,listOfRhythms.get(0)+1);
                listOfRhythms.set(26,listOfRhythms.get(26)+1);
                break;
            case 2:
                listOfRhythms.set(27,listOfRhythms.get(27)+1);
                if(quarter.get(0).length() <= 2.0 && quarter.get(1).length() <= 2.0){
                    listOfRhythms.set(1,listOfRhythms.get(1)+1);
                }else if(quarter.get(0).length() <= 2 && quarter.get(1).length() >= 3.0){
                    listOfRhythms.set(5,listOfRhythms.get(5)+1);
                }else if(quarter.get(0).length() >= 3.0 && quarter.get(1).length() <= 2){
                    listOfRhythms.set(6,listOfRhythms.get(6)+1);
                }else if(quarter.get(0).length() <= 1.5 && quarter.get(1).length() >= 2.5){
                    listOfRhythms.set(8,listOfRhythms.get(8)+1);
                }else if(quarter.get(0).length() >= 2.5 && quarter.get(1).length() <= 1.5){
                    listOfRhythms.set(24,listOfRhythms.get(24)+1);
                }
                break;
            case 3:
                listOfRhythms.set(28,listOfRhythms.get(28)+1);
                if(quarter.get(0).length() >= 2.5 && quarter.get(0).length() <= 3.5 && quarter.get(1).length() >= 2.5 &&  quarter.get(1).length() <= 3.5 && quarter.get(2).length() >= 2.5 &&  quarter.get(2).length() <= 3.5){
                    listOfRhythms.set(9,listOfRhythms.get(9)+1);
                }else if(quarter.get(0).length() <= 2.0 && quarter.get(1).length() >= 4.5 && quarter.get(2).length() <= 3.0){
                    listOfRhythms.set(10,listOfRhythms.get(10)+1);
                }else if(quarter.get(0).length() >= 4.5 && quarter.get(1).length() <= 2.0 && quarter.get(2).length() <= 3.0){
                    listOfRhythms.set(11,listOfRhythms.get(11)+1);
                }else if(quarter.get(0).length() <= 3  && quarter.get(1).length() <= 2.0 && quarter.get(2).length() >= 4.5){
                    listOfRhythms.set(12,listOfRhythms.get(12)+1);
                }else if(quarter.get(0).length() <= 3 && quarter.get(1).length() >= 4.5 && quarter.get(2).length() <= 2.0){
                    listOfRhythms.set(13,listOfRhythms.get(13)+1);
                }else if(quarter.get(0).length() <= 1.5 && quarter.get(1).length() >= 4.0 && quarter.get(2).length() >= 4.0){
                    listOfRhythms.set(25,listOfRhythms.get(25)+1);
                }else if(quarter.get(0).length() <= 2.0){
                    listOfRhythms.set(2,listOfRhythms.get(2)+1);
                }else if(quarter.get(1).length() <= 2.0 ){
                    listOfRhythms.set(3,listOfRhythms.get(3)+1);
                }else if(quarter.get(2).length() <= 2.0){
                    listOfRhythms.set(4,listOfRhythms.get(4)+1);
                }

                break;
            case 4:
                listOfRhythms.set(29,listOfRhythms.get(29)+1);
                if(quarter.get(0).length() >= 3.0 && quarter.get(1).length() >= 3.0 && quarter.get(2).length() >= 3.0 && quarter.get(3).length() >= 3.0){
                    listOfRhythms.set(7,listOfRhythms.get(7)+1);
                }else if( quarter.get(2).length() <= 3.0 && quarter.get(3).length() <= 3.0){
                    listOfRhythms.set(14,listOfRhythms.get(14)+1);
                }else if( quarter.get(2).length() <= 2.0){
                    listOfRhythms.set(15,listOfRhythms.get(15)+1);
                }else if( quarter.get(3).length() <= 2.0){
                    listOfRhythms.set(16,listOfRhythms.get(16)+1);
                }else if( quarter.get(0).length() <= 3.0 && quarter.get(3).length() <= 3.0){
                    listOfRhythms.set(19,listOfRhythms.get(19)+1);
                }else if( quarter.get(0).length() <= 3.0 && quarter.get(1).length() <= 3.0){
                    listOfRhythms.set(21,listOfRhythms.get(21)+1);
                }else if( quarter.get(0).length() <= 2.0 ){
                    listOfRhythms.set(22,listOfRhythms.get(22)+1);
                }else if( quarter.get(1).length() <= 2.0){
                    listOfRhythms.set(23,listOfRhythms.get(23)+1);
                }

                break;

            case 5:
                if(quarter.get(4).length() <= 4.0){
                    listOfRhythms.set(17,listOfRhythms.get(17)+1);
                }else if(quarter.get(0).length() <= 4.0){
                    listOfRhythms.set(20,listOfRhythms.get(20)+1);
                }
                break;

            case 6:
                listOfRhythms.set(18,listOfRhythms.get(18)+1);
                break;
            default:
        }

    }

    public void TrochaicCheck(ArrayList<MidiNote> quarter, int keySig, boolean majorKey){
        int diff =halfToneToTone(quarter.get(0).note(), quarter.get(1).note(), keySig, majorKey);
        int keyChromatic = chromaticToDiatonic(quarter.get(0).note(), keySig, majorKey);
        String sharpNotater = "";
        if (diff< 0) sharpNotater = ".";
        diff =  Math.abs(diff)+1;
        if(diff>=8) return;

        String key = 1+ "" + Math.abs(diff)  + sharpNotater;

        if(mapOfTrochees.get(key) == null){
            HashMap<Integer, Integer> newHM = new HashMap<Integer, Integer>();
            newHM.put(keyChromatic, 1);
            mapOfTrochees.put(key,newHM);

        }else{
            if(mapOfTrochees.get(key).get(keyChromatic) != null){
                int count = mapOfTrochees.get(key).get(keyChromatic);
                mapOfTrochees.get(key).put(keyChromatic,count+1);
            }else{
                mapOfTrochees.get(key).put(keyChromatic,1);
            }


        }

    }

    public void DactylCheck(ArrayList<MidiNote> quarter, int keySig, boolean majorKey){
        int diff1 =halfToneToTone(quarter.get(0).note(), quarter.get(1).note(), keySig, majorKey);
        int diff2 =halfToneToTone(quarter.get(0).note(), quarter.get(2).note(), keySig, majorKey);
        int keyChromatic = chromaticToDiatonic(quarter.get(0).note(), keySig, majorKey);
        String sharpNotater1 = "";
        String sharpNotater2= "";
        if (diff1< 0) sharpNotater1 = ".";
        if (diff2< 0) sharpNotater2 = ".";
        diff1 =  Math.abs(diff1)+1;
        diff2 =  Math.abs(diff2)+1;

        if(diff1>=8 ||diff2>=8 ) return;

        String key = 1+ "" + Math.abs(diff1) + sharpNotater1+ Math.abs(diff2) + sharpNotater2;

        if(mapOfDactyles.get(key) == null){
            HashMap<Integer, Integer> newHM = new HashMap<Integer, Integer>();
            newHM.put(keyChromatic, 1);
            mapOfDactyles.put(key,newHM);

        }else{
            if(mapOfDactyles.get(key).get(keyChromatic) != null){
                int count = mapOfDactyles.get(key).get(keyChromatic);
                mapOfDactyles.get(key).put(keyChromatic,count+1);
            }else{
                mapOfDactyles.get(key).put(keyChromatic,1);
            }


        }

    }

    public String keyToString(int keyIn){
        String key = "";
        switch(keyIn){
            case 0: key = "c"; break;
            case 1: key = "c#"; break;
            case 2: key = "d"; break;
            case 3: key = "d#"; break;
            case 4: key = "e"; break;
            case 5: key = "f"; break;
            case 6: key = "f#"; break;
            case 7: key = "g"; break;
            case 8: key = "g#"; break;
            case 9: key = "a"; break;
            case 10: key = "a#"; break;
            case 11: key = "b"; break;
            case 12: key = "c"; break;
            default: key = "c"; break;
        }
        return key;
    }

    public int rootNote(int keySig, boolean majorKey){
        int root = 0;
        if(majorKey){
            switch(keySig){
                case 0: root = 0; break;
                case 1: root = 7; break;
                case 2: root = 2; break;
                case 3: root = 9; break;
                case 4: root = 4; break;
                case 5: root = 11; break;
                case 6: root = 6; break;
                case -1: root = 5; break;
                case -2: root = 10; break;
                case -3: root = 3; break;
                case -4: root = 8; break;
                case -5: root = 1; break;
                case -6: root = 6; break;
                default: root = 0; break;
            }
        }else{
            switch(keySig){
                case 0: root = 9; break;
                case 1: root = 4; break;
                case 2: root = 11; break;
                case 3: root = 6; break;
                case 4: root = 1; break;
                case 5: root = 8; break;
                case 6: root = 3; break;
                case -1: root = 2; break;
                case -2: root = 7; break;
                case -3: root = 0; break;
                case -4: root = 5; break;
                case -5: root = 10; break;
                case -6: root = 3; break;
                default: root = 9; break;
            }
        }
        return root;
    }

    public void addKeySig(int keySig, boolean majorKey){
        String key = "" +keyToString(rootNote(keySig,majorKey));
        if(majorKey){
            key += " major";
        }else{
            key += " minor";
        }

        if(mapOfKeys.get(key) == null){
            mapOfKeys.put(key,1);
        }else{
            mapOfKeys.put(key,mapOfKeys.get(key)+1);
        }
    }

    public void addTimeSig(int timeSigNumerator, int timeSigDenominator){
        String key = timeSigNumerator + "/" + timeSigDenominator;
        if(mapOfTimeSigs.get(key) == null){
            mapOfTimeSigs.put(key,1);
        }else{
            mapOfTimeSigs.put(key,mapOfTimeSigs.get(key)+1);
        }

    }

    public int keySigCheck(int note, int keySig, boolean majorKey){

        int root =rootNote(keySig,majorKey);
        note%= 12;
        if(note < 0) note += 12;
        if(note < root) note += 12;
        return note-root;

    }

    public int chromaticToDiatonic(int note, int keySig,boolean majorKey){
        int out = 1;
        switch(keySigCheck(note, keySig, majorKey)){
            case 0: out = 1; break;
            case 1: out = 2; break;
            case 2: out = 2; break;
            case 3: out = 3; break;
            case 4: out = 3; break;
            case 5: out = 4; break;
            case 6: out = 5; break;
            case 7: out = 5; break;
            case 8: out = 6; break;
            case 9: out = 6; break;
            case 10: out = 7; break;
            case 11: out = 7; break;
            case 12: out = 8; break;
            default: out = 1; break;
        }
        return out;
    }


    public int halfToneToTone(int firstNote, int secondNote, int keySig, boolean majorKey){
        int firstTone = 0;
        int secondTone = 0;

        firstTone = chromaticToDiatonic(firstNote, keySig, majorKey);
        firstTone = firstTone + (7 * (int) Math.round((keySigCheck(0, keySig, majorKey)+firstNote)/12));

        secondTone = chromaticToDiatonic(secondNote, keySig, majorKey);
        secondTone = secondTone + (7 * (int) Math.round((keySigCheck(0,keySig, majorKey)+secondNote)/12));

        return (secondTone - firstTone);
    }

    public void clearAnalytics(){

    }

    public ArrayList<Integer> listOfTones(){
        return listOfTones;
    }

    public ArrayList<Integer> listOfRhythms(){
        return listOfRhythms;
    }

    public TreeMap<String,HashMap<Integer, Integer>> mapOfTrochees(){
        return mapOfTrochees;
    }

    public TreeMap<String,HashMap<Integer, Integer>> mapOfDactyles(){
        return mapOfDactyles;
    }

    public TreeMap<String, Integer> mapOfKeys(){
        return mapOfKeys;
    }

    public TreeMap<String, Integer> mapOfTimeSigs(){
        return mapOfTimeSigs;
    }

    public void filterTimeSig(ObservableList<String> items){
        this.filterTimeSig = items;
    }

    public void filterKeySig(ObservableList<String> items){
        this.filterKeySig = items;
    }

    public void filterMajorSig(ObservableList<String> items){
        this.filterMajorSig = items;
    }


}

class SortEventByTick implements Comparator<MidiEvent>{

        public int compare(MidiEvent a, MidiEvent b){
            int out = 0;
            if(a.getTick() < b.getTick()){
                out = -1;
            }else if(a.getTick() > b.getTick()){
                out = 1;
            }
            return out;
        }
}
