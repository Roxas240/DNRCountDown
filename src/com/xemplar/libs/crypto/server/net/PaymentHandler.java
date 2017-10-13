package com.xemplar.libs.crypto.server.net;

import com.xemplar.libs.crypto.common.NetworkListener;
import com.xemplar.libs.crypto.server.domain.Transaction;
import com.xemplar.libs.crypto.server.net.domain.Request;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.xemplar.libs.crypto.server.net.CryptoServer.MIN_CONFIRMS;

/**
 * Created by Rohan on 8/20/2017.
 */
public class PaymentHandler implements Runnable{
    private double confirms, prevConfirmes;
    private boolean started, canceled;
    private CryptoServer main;
    private Request request;
    private int id;

    public PaymentHandler(CryptoServer rest, Request req, NetworkListener.ServerPaymentListener listener){
        this.main = rest;
        this.payListener = listener;
        this.price = new BigDecimal(req.getAmount());
        this.request = req;
        this.id = req.id;
        this.canceled = false;
    }

    private NetworkListener.ServerPaymentListener payListener;
    private BigDecimal price;
    private int startBlock;
    private long startTime;
    public void run() {
        this.started = true;

        String pin = randomString(6);
        while (!main.checkCode(pin)) {
            pin = randomString(6);
        }
        this.startBlock = main.link.getBlockCount();
        this.startTime = System.currentTimeMillis();

        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("id", "" + id));
        pairs.add(new BasicNameValuePair("pin", pin));
        main.link.doPost("setpin", pairs, null);
        main.listener.onRequestGenerated(request.id, pin);

        List<String> hasTXNote = new ArrayList<>();
        int lastBlock = startBlock - 1, currentBlock = startBlock;
        BigDecimal bal = new BigDecimal("0.0");
        boolean paid = false;

        List<String> allTX = new ArrayList<>();
        while (!paid && !canceled) {
            if (currentBlock != lastBlock) {
                List<String> received = main.link.getPayTX(pin, lastBlock, currentBlock);
                if (received != null) {
                    hasTXNote.addAll(received);
                    allTX.addAll(received);
                }
                if (bal.compareTo(price) >= 0) {
                    paid = true;
                }
            }
            lastBlock = currentBlock;
            currentBlock = main.link.getBlockCount();

            List<String> added = new ArrayList<>();
            for (String id : hasTXNote) {
                Transaction b = main.link.getTransaction(id);
                int confirm = b.getConfirmations();
                if(confirm > 0){

                }
                if (b.getConfirmations() < MIN_CONFIRMS) continue;

                bal = bal.add(b.getAmount());
                added.add(id);
            }
            hasTXNote.removeAll(added);
            added.clear();

            confirms = 0;
            for (String id : allTX) {
                Transaction b = main.link.getTransaction(id);
                int confirm = b.getConfirmations();
                if(confirm > 0) {
                    confirms += confirm;
                }
            }
            confirms /= (double)allTX.size();
            if(prevConfirmes != confirms){
                List<NameValuePair> dat = new ArrayList<>();
                dat.add(new BasicNameValuePair("id", id + ""));
                dat.add(new BasicNameValuePair("confirms", (int)confirms + ""));
                main.link.doPost("confirms", dat, null);
                main.listener.onRequestUpdate(request.id, "Confirm: " + (int)confirms);
                dat.clear();
            }
            prevConfirmes = confirms;

            sleep(1000);
        }
        allTX.clear();

        if (paid) {
            main.listener.onRequestUpdate(request.id, "Filled");
            payListener.paymentReceived(this, "");
            main.codes.remove(pin);
            hasTXNote.clear();
        }
    }


    String randomString(int length){
        final String master = "1234567890";
        Random r = new Random();
        String ret = "";
        for(int i = 0; i < length; i++){
            ret += master.charAt(r.nextInt(master.length()));
        }

        return ret;
    }

    public void cancel(){
        canceled = true;
        payListener.paymentCanceled(this);
    }
    public int getID(){
        return id;
    }
    public String getAmount(){
        return price.toPlainString();
    }
    public boolean hasRequest(Request req){
        return this.request.getId() == req.getId();
    }
    public boolean hasStarted(){
        return started;
    }
    public static void sleep(long mills){
        try{
            Thread.sleep(mills);
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
