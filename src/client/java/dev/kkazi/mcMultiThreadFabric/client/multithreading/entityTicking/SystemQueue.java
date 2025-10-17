package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import com.ibm.icu.text.RuleBasedNumberFormat;

//idee hinter dieser Queue  ist das sie nur dann ein item abgibt wenn
//die richtigen slots voll sind
public class SystemQueue {
    private int currPtr = 0;
    //writeRequests to world maybe consumer etc.
    private Runnable[] writeReq;

    public SystemQueue(int size){
        writeReq = new Runnable[size];
    }


    // wenn das in einer loop ist und wir alle writerequest and die selbe stelle schreiben an die sich auch die Objekte
    // in der entitylist befinden dann wird oreder by behalten
    public void runNext(){
        if (writeReq[currPtr] == null){
            return;
        }
        writeReq[currPtr].run();
        ++currPtr;
    }

    public void addRun(Runnable runnable, int pos){
        writeReq[pos] = runnable;
    }
}
