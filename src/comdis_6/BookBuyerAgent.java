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

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.HashMap;
import java.util.Map;

public class BookBuyerAgent extends Agent {
    // Type of the agent
    private final String agentType = "book-buying";
    
    // Type of the offer message
    private final String offerMessageType = "book-offer";
    
    // Type of the trade message
    private final String tradeMessageType = "book-trade";
    
    // The title of the book to buy
    private Map<String, Integer> targetBooks;
    
    // The GUI by means of which the user can add books in the catalogue
    private BookBuyerGUI myGui;

    // Put agent initializations here
    @Override
    protected void setup() {
        // Register the book-buying service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(agentType);
        sd.setName("JADE-book-trading-"+System.currentTimeMillis());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Create and show the GUI 
        myGui = new BookBuyerGUI(this);
        myGui.showGui();
        
        // Printout a welcome message
        System.out.println("Hallo! Buyer-agent " + getAID().getName() + " is ready.");
        targetBooks = new HashMap<>();

        // Add the behaviour serving queries from buyer agents
        addBehaviour(new OfferRequestsHandler());

        // Add the behaviour serving purchase orders from buyer agents
        addBehaviour(new PurchaseOrdersHandler());
    }

    // Put agent clean-up operations here
    @Override
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
            // Printout a dismissal message
            System.out.println("Buyer-agent " + getAID().getName() + " terminating.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
    
    

    /**
     * This is invoked by the GUI when the user adds a new book for sale
     * @param title
     * @param price
     */
    public void updateTargetBooks(String title, int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                targetBooks.put(title, price);
                System.out.println(title + " inserted into targetBooks. Price = " + price);
            }
        });
    }
    
    public String getTitle(ACLMessage message){
        return message.getContent().split("-")[0];
    }
    
    public Integer getPrice(ACLMessage message){
        return Integer.getInteger(message.getContent().split("-")[1]);
    }
    

    /**
     * Inner class OfferRequestsHandler. This is the behaviour used by
     * Book-buyer agents to serve incoming requests for offer from seller
     * agents. If the requested book is in the local interest list the buyer agent
     * and the price is within the maximun set replies with a PROPOSE message 
     * Otherwise a REFUSE message is sent back.
     */
    private class OfferRequestsHandler extends CyclicBehaviour {

        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId(offerMessageType));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                String title = getTitle(msg);
                Integer price = getPrice(msg);
                ACLMessage reply = msg.createReply();
                
                // We want the book proposed. Set response to propose
                if (title != null && price != null && 
                        targetBooks.keySet().contains(title) && targetBooks.get(title) <= price)
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                // We dont want the book. Set response to refuse
                else
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                reply.setContent("not-available");
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }  // End of inner class OfferRequestsHandler

    /**
     * Inner class PurchaseOrdersHandler. This is the behaviour used by
     * Book-buyer agents to serve incoming offer acceptances (i.e. purchase
     * orders) from seller agents. The Buyer agent removes the purchased book
     * from its wanted list and replies with an INFORM message to notify the seller
     * that the purchase has been sucesfully completed.
     */
    private class PurchaseOrdersHandler extends CyclicBehaviour {

        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                    MessageTemplate.MatchConversationId(tradeMessageType));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // PROPOSE Message received. Process it
                String title = getTitle(msg);
                Integer price = getPrice(msg);
                ACLMessage reply = msg.createReply();
                
                // We accept the transaction. Set response to AGREE
                if (title != null && price != null && 
                        targetBooks.keySet().contains(title) && targetBooks.get(title) <= price) {
                    reply.setPerformative(ACLMessage.AGREE);
                    System.out.println("Accepted to buy " +title + " for " + price + "€ from " + msg.getSender().getName());
                } 
                // We reject the transaction. Set response to REFUSE
                else
                    reply.setPerformative(ACLMessage.REFUSE);
                
                reply.setContent("not-available");
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }  // End of inner class PurchaseOrdersHandler
}
