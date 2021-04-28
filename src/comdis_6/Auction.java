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
    private int price, round;
    private final int increment;
    private final long id;
    private List<AID> lastRoundBuyers, buyers;
    private ACLMessage cfp;

    public Auction(String title, int price, int increment) {
        this.id = System.currentTimeMillis();
        this.title = title;
        this.round = 0;
        this.price = price;
        this.increment = increment;
        this.lastRoundBuyers = null;
        this.buyers = new ArrayList<>();
        this.cfp = new ACLMessage(ACLMessage.CFP);
        this.cfp.setContent(this.title + "-" + price);
        this.cfp.setConversationId("book-offer");
        this.cfp.setReplyWith("cfp-" + this.id + "-" + this.round);
    }

    public String getTitle() {
        return title;
    }

    public long getId() {
        return id;
    }

    public int getOriginalPrice() {
        return price;
    }

    public int getCurrentPrice() {
        return price + (increment * round);
    }

    public int getLastRoundPrice() {
        return price + (increment * (round - 1));
    }

    public int getIncrement() {
        return increment;
    }

    public List<AID> getLastRoundBuyers() {
        return lastRoundBuyers;
    }

    public List<AID> getBuyers() {
        return buyers;
    }

    public Auction setBuyers(List<AID> buyers) {
        this.lastRoundBuyers = new ArrayList<>(buyers);
        this.buyers = buyers;
        return this;
    }

    public ACLMessage getCFP() {
        return cfp;
    }

    public Auction setCFP(ACLMessage cfp) {
        this.cfp = cfp;
        return this;
    }
    
    public Auction incrementPrice(){
        this.price += this.increment;
        return this;
    }
    
    public Auction incrementRound(){
        this.round += 1;
        return this;
    }
    
    public int getRound(){
        return this.round;
    }
    
    public Auction resetCFP(){
        this.cfp = new ACLMessage(ACLMessage.CFP);
        this.cfp.setContent(this.title + "-" + this.getCurrentPrice());
        this.cfp.setConversationId("book-offer");
        this.cfp.setReplyWith("cfp-" + this.id + "-" + this.round); // Unique value
        return this;
    }
    
    public Auction resetAuction(){
        this.round = 0;
        this.lastRoundBuyers = null;
        this.buyers = new ArrayList<>();
        this.resetCFP();
        return this;
    }
    
    @Override
    public String toString() {
        String toret = "Auction\n{" + "id=" + id + "bookTitle=" + title + ", price=" + price + "€, increment=" + increment + "€, Round=" + round;
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
        hash = 41 * hash + (int) (this.id ^ (this.id >>> 32));
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
