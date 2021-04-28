/** ***************************************************************
 * JADE - Java Agent DEvelopment Framework is a framework to develop
 * multi-agent systems in compliance with the FIPA specifications.
 * Copyright (C) 2000 CSELT S.p.A.  *
 * GNU Lesser General Public License
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation,
 * version 2.1 of the License.  *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 **************************************************************** */
package comdis_6;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class BookSellerAgent extends Agent {
    // The catalogue of books for sale (maps the title of a book to its object)
    private List<Auction> catalogue;
    
    // The repository of successful transactions
    private Map<String, Auction> repository;
    
    // The GUI by means of which the user can add books in the catalogue
    private BookSellerGui myGui;
    
    // The template ofr sendind CFP
    DFAgentDescription templateCFP;
    ServiceDescription sdCFP;

    // Put agent initializations here
    @Override
    protected void setup() {
        //***********************************   INITIAL SETUP   ***********************************
        // Create the catalogue and repository
        catalogue = new ArrayList<>();
        repository = new HashMap<>();

        // Create and show the GUI 
        myGui = new BookSellerGui(this);
        myGui.showGui();
        
        // Set the CFP template and Service
        templateCFP = new DFAgentDescription();
        sdCFP = new ServiceDescription();
        sdCFP.setType("book-buying");
        templateCFP.addServices(sdCFP);

        // Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        sd.setName("JADE-book-seller");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.exit(1);
        }

        //***********************************   DURING THE ROUND BEHAVIOUR  ***********************************
        // Add the behaviour announcing auctions to buyers
        addBehaviour(new AnnounceAuctionsServer());


        //***********************************   EACH 10 SECONDS BEHAVIOUR  ***********************************
        // Add a TickerBehaviour that schedules a request to seller agents every 10 seconds
        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                //We use an iterator so we can remove the current auction from the catalogue
                Iterator<Auction> auctionIt = catalogue.iterator();
                Auction auction;
                
                //For each of the auctions
                while (auctionIt.hasNext()) {
                    auction = auctionIt.next();
                    
                    //***********************************   END OF ROUND BEHAVIOUR   ***********************************
                    //If there was a round before the current one we need to check the potential buying proposals
                    if (auction.getCFP() != null) {
                        //We clear this round's buyers and it gets saved in lastRoundBuyers array
                        auction.setBuyers(new ArrayList<>());

                        // Prepare the template to get proposals
                        MessageTemplate mt = MessageTemplate.and(
                                MessageTemplate.MatchConversationId("book-offer"),
                                MessageTemplate.MatchInReplyTo(auction.getCFP().getReplyWith()));
                        // Receive all proposals/refusals from buyer agents
                        ACLMessage reply = myAgent.receive(mt);
                        
                        // if there is no proposals the Auction ends and the first buyer from the previous round wins 
                        if (reply == null) {
                            // If lastRoundBuyers is empty, and there were no replies, we reset the auction
                            if(auction.getLastRoundBuyers().isEmpty()){
                                auction.resetAuction();
                                continue;
                            }
                            // We remove the auction from the list
                            auctionIt.remove();
                            // Add the behaviour starting the trade 
                            addBehaviour(new TradeController(auction, "last round"));
                            continue;
                        }
                        
                        //We save the proposals
                        while (reply != null) {
                            // Reply received, we add the sender to the buyers list
                            if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL && !auction.getBuyers().contains(reply.getSender()))
                                auction.getBuyers().add(reply.getSender());
                            reply = myAgent.receive(mt);
                        }
                        
                        //If there is only one porposal the buyer wins the auction
                        if (auction.getBuyers().size() == 1) {
                            // We remove the auction from the list
                            auctionIt.remove();
                            // Add the behaviour starting the trade 
                            addBehaviour(new TradeController(auction, "this round"));
                            continue;
                        }
                    }

                    //***********************************   NEW ROUND BEHAVIOUR   ***********************************
                    // if there is more than one buyer ot CFP is null we create a new CFP
                    // Increment round and set new CFP
                    auction.incrementRound();
                    auction.setCFP(new ACLMessage(ACLMessage.CFP));
                    auction.getCFP().setContent(auction.getTitle() + "-" + auction.getCurrentPrice());
                    auction.getCFP().setConversationId("book-offer");
                    auction.getCFP().setReplyWith("cfp-" + auction.getId() + "-" + auction.getRound()); // Unique value
                    System.out.println("New auction round for " + auction.getTitle() + " : " + auction.getId() + " for " + auction.getCurrentPrice() + "€");

                    // Update the list of CFP receivers
                    try {
                        //Get them all
                        DFAgentDescription[] result = DFService.search(myAgent, templateCFP);
                        System.out.println("Found the following book-buying agents:");
                        for (int i = 0; i < result.length; ++i) {
                            auction.getCFP().addReceiver(result[i].getName());
                            System.out.println(" "+auction.getBuyers().get(i).getName());
                        }
                        myAgent.send(auction.getCFP());
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                }
            }
        });
    }
    
     /**
     * Inner class TradeController. This is the behaviour used by Book-seller
     * agents to controll the sell.
     */
    private class TradeController extends Behaviour {

        private final Auction auction;
        private final String option;
        private MessageTemplate mt; // The template to receive replies
        private ACLMessage reply;
        private Boolean isDone;

        private TradeController(Auction auction, String option) {
            super();
            this.auction = auction;
            this.option = option;
            this.isDone = false;
            this.reply = null;
        }
        
        public void finishTrade(){
            isDone = true;
            
            // Purchase order reply received
            if (reply.getPerformative() == ACLMessage.AGREE) {
                // Purchase successful. We can terminate
                System.out.println(auction.getTitle() + " successfully purchased from agent " + reply.getSender().getName());
                System.out.println("Price = " + auction.getLastRoundPrice());
                repository.put(reply.getInReplyTo(), auction);
                return;
            }
            
            // Purchase unsuccessful. We reset the auction and return it to the catalogue
            System.out.println("Attempt failed: buyer no longer interested");
            auction.resetAuction();
            catalogue.add(auction);
        }

        public void action() {
            // If the Behaviour was blocked while waiting for the answer and has been reawaken
            if (reply != null)
                this.finishTrade();
            if(isDone)
                return;
            
            // Set the purchase order
            ACLMessage order = new ACLMessage(ACLMessage.PROPOSE);
            order.setConversationId("book-trade");
            AID seller = (option.equals("last round")) ? auction.getLastRoundBuyers().get(0) : auction.getBuyers().get(0);
            order.addReceiver(seller);
            String content = (option.equals("last round")) ? auction.getTitle() + "-" + auction.getLastRoundPrice(): auction.getTitle() + "-" + auction.getCurrentPrice();
            order.setContent(content);
            order.setReplyWith("order-" + System.currentTimeMillis());
            
            // Send the purchase order to the seller that provided the best offer
            myAgent.send(order);
            
            // Prepare the template to get the purchase order reply
            mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("book-trade"),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
            
            // Receive the purchase order reply
            reply = myAgent.receive(mt);
            
            // If it wasn't recceived we wait, if it was we finish the trade
            if (reply != null)
                this.finishTrade();
            else
                block();
        }

        public boolean done() {
            return (isDone);
        }
    }  // End of inner class RequestPerformer

    // Put agent clean-up operations here
    @Override
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        
        // Close the GUI
        myGui.dispose();
        
        // Printout a dismissal message
        System.out.println("Seller-agent " + getAID().getName() + " terminating.");
    }

    /**
     * This is invoked by the GUI when the user adds a new book for sale
     *
     * @param newAuction
     */
    public void updateCatalogue(Auction newAuction) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.add(newAuction);
                System.out.println(newAuction.getTitle() + " inserted with catalogue id " + newAuction.getId() + ". Price = " + newAuction.getOriginalPrice());
            }
        });
    }

    /**
     * Inner class AnnounceAuctionsServer. This is the behaviour used by
     * Book-seller agents to announce the available autions so anyone can
     * dynamically get in or out until the round is over
     */
    private class AnnounceAuctionsServer extends CyclicBehaviour {

        //***********************************   DURING THE ROUND   ***********************************
        @Override
        public void action() {
            //We use an iterator so we can removing the current auction from the catalogue doesnt break the loop
            Iterator<Auction> auctionIt = catalogue.iterator();
            Auction auction;
            
            //For each of the auctions
            while (auctionIt.hasNext()) {
                auction = auctionIt.next();
                    
                // Update the list of CFP receivers
                try {
                    //Get them all
                    DFAgentDescription[] result = DFService.search(myAgent, templateCFP);
                    System.out.println("Found the following book-buying agents:");
                    for (int i = 0; i < result.length; ++i) {
                        auction.getCFP().clearAllReceiver();
                        auction.getCFP().addReceiver(result[i].getName());
                        System.out.println(" "+auction.getBuyers().get(i).getName());
                    }
                    myAgent.send(auction.getCFP());
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        }
    }  // End of inner class AnnounceAuctionsServer
}
