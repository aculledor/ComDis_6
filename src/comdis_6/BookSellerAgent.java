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
    // The catalogue of books for sale (maps the title of a book to its price)
    private HashMap<String, Integer> catalogue;
    private Map<String, Auction> catalogue_2;
    // The GUI by means of which the user can add books in the catalogue
    private BookSellerGui myGui;
    // Id of the auctiosn
    private int idCounter;

    // Put agent initializations here
    @Override
    protected void setup() {
        // Create the catalogue
        catalogue = new HashMap();
        catalogue_2 = new HashMap<>();

        // Create and show the GUI 
        myGui = new BookSellerGui(this);
        myGui.showGui();
        
        //Start the idCounter
        idCounter = 0;

        // Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        sd.setName("JADE-book-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.exit(1);
        }

        // Add the behaviour serving queries from buyer agents
        addBehaviour(new OfferRequestsServer());

        // Add the behaviour serving purchase orders from buyer agents
        addBehaviour(new PurchaseOrdersServer());
        
        // Add a TickerBehaviour that schedules a request to seller agents every 10 seconds
        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick() {
                //We use an iterator so we can remove the current auction from the catalogue
                Iterator<Auction> auctionIt = catalogue_2.values().iterator();
                Auction auction;
                //For each of the auctions
                while(auctionIt.hasNext()){
                    auction = auctionIt.next();
                    //***********************************   STEP 1   ***********************************
                    //If there was a round before the current one we need to check the potential buying proposals
                    if(auction.getCfp() != null){
                        //We clear this round's buyers and it gets saved in lastRoundBuyers array
                        auction.setBuyers(new ArrayList<>());
                        
                        // Prepare the template to get proposals
                        MessageTemplate mt; // The template to receive replies
                        mt = MessageTemplate.and(
                                MessageTemplate.MatchConversationId("book-offer"),
                                MessageTemplate.MatchInReplyTo(auction.getCfp().getReplyWith()));

                        // Receive all proposals/refusals from buyer agents
                        ACLMessage reply = myAgent.receive(mt);
                        //iv there is no proposals the Auction ends and the first buyer from the previous round wins 
                        if(reply != null){
                            
                        }
                        //We save the proposals
                        while(reply != null){
                            // Reply received, we add the sender to the buyers list
                            if (reply.getPerformative()== ACLMessage.PROPOSE)
                                auction.getBuyers().add(reply.getSender());
                            reply = myAgent.receive(mt);
                        }
                        //If there is only one porposal the buyer wins the auction
                        if(auction.getBuyers().size() == 1){
                            
                        }
                    }
                    
                    //***********************************   STEP 0   ***********************************
                    System.out.println("New auction round for " + auction.getTitle());
                    // Update the list of seller agents
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("book-buying");
                    template.addServices(sd);
                    try {
                        //Get them all
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following buying agents:");
                        auction.setBuyers(new ArrayList<>());
                        for (int i = 0; i < result.length; ++i) {
                            auction.getBuyers().add(result[i].getName());
                            System.out.println(auction.getBuyers().get(i).getName());
                        }
                        
                        // Send the cfp to all buyers, type book-offer
                        auction.setCfp(new ACLMessage(ACLMessage.CFP));
                        for(AID buyer : auction.getBuyers())
                            auction.getCfp().addReceiver(buyer);
                        auction.getCfp().setContent(auction.getTitle());
                        auction.getCfp().setConversationId("book-offer");
                        auction.getCfp().setReplyWith("cfp" + auction.getId()); // Unique value
                        myAgent.send(auction.getCfp());
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    
                                        
                    //***********************************   STEP 2   ***********************************
                }
            }
        });
    }

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
     * @param newAuction
     */
    public void updateCatalogue(Auction newAuction) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue_2.put(newAuction.getTitle(), newAuction);
                System.out.println(newAuction.getTitle() + " inserted with catalogue id "+newAuction.getId()+". Price = " + newAuction.getPrice());
                idCounter++;
            }
        });
    }
    
    
    public int getIdCounter(){
        return idCounter;
    }

    /**
     * Inner class OfferRequestsServer. This is the behaviour used by
     * Book-seller agents to serve incoming offers from buyer
     * agents. If the requested book is in the local catalogue the seller agent
     * replies with a PROPOSE message specifying the price. Otherwise a NOT-UNDERSTOOD
     * message is sent back.
     */
    private class OfferRequestsServer extends CyclicBehaviour {

        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = (Integer) catalogue.get(title);
                if (price != null) {
                    // The requested book is available for sale. Reply with the price
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(price.intValue()));
                } else {
                    // The requested book is NOT available for sale.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }  // End of inner class OfferRequestsServer

    /**
     * Inner class PurchaseOrdersServer. This is the behaviour used by
     * Book-seller agents to serve incoming offer acceptances (i.e. purchase
     * orders) from buyer agents. The seller agent removes the purchased book
     * from its catalogue and replies with an INFORM message to notify the buyer
     * that the purchase has been sucesfully completed.
     */
    private class PurchaseOrdersServer extends CyclicBehaviour {

        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = catalogue.remove(title);
                if (price != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(title + " sold to agent " + msg.getSender().getName());
                } else {
                    // The requested book has been sold to another buyer in the meanwhile .
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }  // End of inner class OfferRequestsServer
    
    
    
    //NEW CLASS THATS A COPY OF THE ORIGINAL NOOKBUYER
    
    
    

    // Put agent clean-up operations here
    @Override
    protected void takeDown() {
        // Printout a dismissal message
        System.out.println("Seller-agent " + getAID().getName() + " terminating.");
    }

    /**
     * Inner class RequestPerformer. This is the behaviour used by Book-buyer
     * agents to request seller agents the target book.
     */
    private class RequestPerformer extends Behaviour {

        private AID bestSeller; // The agent who provides the best offer 
        private int bestPrice;  // The best offered price
        private int repliesCnt = 0; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the cfp to all sellers
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);
                    }
                    cfp.setContent(targetBookTitle);
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive all proposals/refusals from seller agents
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // This is an offer 
                            int price = Integer.parseInt(reply.getContent());
                            if (bestSeller == null || price < bestPrice) {
                                // This is the best offer at present
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            // We received all replies
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    // Send the purchase order to the seller that provided the best offer
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    // Prepare the template to get the purchase order reply
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Receive the purchase order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Purchase successful. We can terminate
                            System.out.println(targetBookTitle + " successfully purchased from agent " + reply.getSender().getName());
                            System.out.println("Price = " + bestPrice);
                            myAgent.doDelete();
                        } else {
                            System.out.println("Attempt failed: requested book already sold.");
                        }

                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("Attempt failed: " + targetBookTitle + " not available for sale");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }  // End of inner class RequestPerformer
}
