/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package comdis_6;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author aculledor
 */
public class Auction {
    private final String title;
    private final int id, price, increment;
    private int step;
    private List<AID> lastRoundBuyers, buyers;
    private ACLMessage cfp;

    public Auction(int id, String title, int price, int increment) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.increment = increment;
        this.step = 0;
        this.lastRoundBuyers = null;
        this.buyers = new ArrayList<>();
        this.cfp = null;
    }

    public String getTitle() {
        return title;
    }

    public int getId() {
        return id;
    }

    public int getPrice() {
        return price;
    }

    public int getIncrement() {
        return increment;
    }

    public int getStep() {
        return step;
    }

    public List<AID> getLastRoundBuyers() {
        return lastRoundBuyers;
    }

    public List<AID> getBuyers() {
        return buyers;
    }

    public Auction setStep(int step) {
        this.step = step;
        return this;
    }

    public Auction setBuyers(List<AID> buyers) {
        this.lastRoundBuyers = new ArrayList<>(buyers);
        this.buyers = buyers;
        return this;
    }

    public ACLMessage getCfp() {
        return cfp;
    }

    public Auction setCfp(ACLMessage cfp) {
        this.cfp = cfp;
        return this;
    }
    
    
    

    @Override
    public String toString() {
        String toret = "Auction\n{" + "id=" + id + "bookTitle=" + title + ", price=" + price + "€, increment=" + increment + "€, step=" + step;
        //lastRoundBuyers
        if(lastRoundBuyers != null && !lastRoundBuyers.isEmpty()){
            toret += "\nlastRoundBuyers {";
            for(AID lastRoundBuyer : lastRoundBuyers){
                toret += "\n\t"+lastRoundBuyer.getName();
            }
            toret += "\n}\n";
        }
        //Buyers
        if(buyers != null && !buyers.isEmpty()){
            toret += "\nBuyers {";
            for(AID buyer : buyers){
                toret += "\n\t"+buyer.getName();
            }
            toret += "\n}\n";
        }
        return toret + "}";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + this.id;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Auction other = (Auction) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
}
