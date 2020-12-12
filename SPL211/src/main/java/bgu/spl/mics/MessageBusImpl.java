package bgu.spl.mics;

import javafx.util.Pair;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@link MessageBusImpl class is the implementation of the MessageBus interface.
 * Write your implementation here!
 * Only private fields and methods can be added to this class.
 */
public class MessageBusImpl implements MessageBus {

    private ConcurrentMap<Class, Pair<LinkedList<ConcurrentLinkedQueue<Message>>, ConcurrentLinkedQueue<Message>>> eventList;
    private ConcurrentMap<MicroService, ConcurrentLinkedQueue<Message>> microServiceQueue;
    private ConcurrentMap<Class, LinkedList<ConcurrentLinkedQueue<Message>>> broadcastList;
    private ConcurrentMap<Message, Future> eventFuture;
    private static MessageBusImpl instance = null;
    private Object lockBroadcast;
    private Object lockEvent;


    private MessageBusImpl() {
        eventList = new ConcurrentHashMap<>();
        microServiceQueue = new ConcurrentHashMap<>();
        broadcastList = new ConcurrentHashMap<>();
        eventFuture = new ConcurrentHashMap<>();
        lockBroadcast = new Object();
        lockEvent = new Object();

    }

    public static MessageBusImpl getInstance() {
        if (instance == null)
            instance = new MessageBusImpl();
        return instance;
    }

    @Override
    public <T> void subscribeEvent(Class<? extends Event<T>> type, MicroService m) {
        //If there is a MicroService that is already subscribed to type {@param type},
        //find the list of MicroServices queues of type {@param type}.
        //and insert m's queue to that list.
        synchronized (this) {
        if (!microServiceQueue.containsKey(m))
            throw new IllegalArgumentException("Service didn't register yet!");
        if (eventList.containsKey(type)) {
            ConcurrentLinkedQueue<Message> mQueue = microServiceQueue.get(m);
            eventList.get(type).getKey().add(mQueue);
        } else {
            LinkedList<ConcurrentLinkedQueue<Message>> list = new LinkedList<>();
            list.add(microServiceQueue.get(m));

            //Create a new pair of the list above and the queue of m.
            Pair<LinkedList<ConcurrentLinkedQueue<Message>>, ConcurrentLinkedQueue<Message>> pair = new Pair<>(list, microServiceQueue.get(m));
            //System.out.println(m.getName()+ " subscribe event"); todo delete
            eventList.put(type, pair);
            }
        }
    }

    @Override
    public void subscribeBroadcast(Class<? extends Broadcast> type, MicroService m) {
        //If there is a MicroService that is already subscribed to type {@param type},
        //find the list of MicroServices queues of type {@param type}.
        //and insert m's queue to that list.
//        if (!microServiceQueue.containsKey(m))todo delete?
//            throw new IllegalArgumentException("Service didn't register yet!");
        synchronized (this) {
            if (broadcastList.containsKey(type)) {
                ConcurrentLinkedQueue<Message> mQueue = microServiceQueue.get(m);
                broadcastList.get(type).add(mQueue);
            } else {
                LinkedList<ConcurrentLinkedQueue<Message>> list = new LinkedList<>();
                list.add(microServiceQueue.get(m));
               // System.out.println(m.getName()+ " subscribe broadcast");todo delete
                broadcastList.put(type, list);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void complete(Event<T> e, T result) {
        if (!eventFuture.containsKey(e))
            throw new IllegalArgumentException("No such event!");
       //resolves e's Future with result.
        else
            eventFuture.get(e).resolve(result);
       // System.out.println("complete");todo delete
    }

    @Override
        public void sendBroadcast(Broadcast b) {
        //Check if any Microservices are subscribed to this
//        if (!broadcastList.containsKey(b.getClass())) {todo delete?
//            throw new IllegalArgumentException("No services subscribed to broadcast!");
//        }
       // System.out.println("start sending broadcast");todo delete

        synchronized (this) {
            //Check if there are MicroServices register to b's type. todo delete
            if (broadcastList.containsKey(b.getClass())) {

                for (ConcurrentLinkedQueue<Message> q : broadcastList.get(b.getClass())) {

                    //Insert b to all the MicroServices that are register to b's type.
//                    if (q!=null)todo delete
                        q.add(b);
                }
            }
           // System.out.println("finish send broadcast");todo delete
     //   synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public <T> Future<T> sendEvent(Event<T> e) {

        synchronized (this) {
   //         System.out.println("start sending event");todo delete
//                 if (!eventList.containsKey(e.getClass()))
//            throw new IllegalArgumentException("No services subscribed to event type!");
            if (eventList.containsKey(e.getClass())) {
                LinkedList<ConcurrentLinkedQueue<Message>> list = eventList.get(e.getClass()).getKey();
                ConcurrentLinkedQueue<Message> roundRobinQueue = eventList.get(e.getClass()).getValue();

                Pair<LinkedList<ConcurrentLinkedQueue<Message>>, ConcurrentLinkedQueue<Message>> pair = new Pair<>(list, roundRobin(list, roundRobinQueue));
                //add e to the relevant(by round-robin) MicroService's queue.
                roundRobinQueue.add(e);


                //Updates the relevant MicrosService's queue.
                eventList.remove(e.getClass());
                eventList.put(e.getClass(), pair);

                Future<T> f = new Future<>();
                eventFuture.put(e, f);
             //   System.out.println("finish sending event");todo delete
                notifyAll();
                return f;
            }
            return null;
        }

    }

    @Override
    public void register(MicroService m) {
        if (microServiceQueue.containsKey(m))
            throw new IllegalArgumentException("Service already registered!");

      //  System.out.println(m.getName() + " register");todo delete
        //Create a new queue for m and insert it to microServiceQueueList.
        ConcurrentLinkedQueue<Message> queue = new ConcurrentLinkedQueue<>();
        microServiceQueue.put(m, queue);
    }

    @Override
    public void unregister(MicroService m) {

        if (!microServiceQueue.containsKey(m))
            throw new IllegalArgumentException("Service never registered!");

        ConcurrentLinkedQueue<Message> mQueue = microServiceQueue.get(m);
        //Removes m's queue from the broadcastList.
       // System.out.println(m.getName()+ " starting unregister");todo delete
            for (Map.Entry<Class, LinkedList<ConcurrentLinkedQueue<Message>>> entry : broadcastList.entrySet()) {
                entry.getValue().remove(mQueue);
            }

            //Removes m and m's queue from microServiceQueueList.
            microServiceQueue.remove(m);


        synchronized (this) {

            //Iterates over eventList and removes mQueue from the relevant lists.
            for (Map.Entry<Class, Pair<LinkedList<ConcurrentLinkedQueue<Message>>, ConcurrentLinkedQueue<Message>>> entry : eventList.entrySet()) {
                //A lock is needed here because the queue that indicates the next iteration in round robintodo delete
                //is a shared object between different threads.todo delete


                //Checks if mQueue is the next relevant queue in round robin.
                if (entry.getValue().getValue() == mQueue) {

                    //Creates a new pair with the next queue in round robin method.
                    Pair<LinkedList<ConcurrentLinkedQueue<Message>>, ConcurrentLinkedQueue<Message>> p = new Pair<>(entry.getValue().getKey(), mQueue);

                    //Updates the eventList with the new pair
                    entry.setValue(p);

                }

                //Removes m's queue from event queue list if it is there.
                ConcurrentLinkedQueue<Message> queue;
                for (Iterator<ConcurrentLinkedQueue<Message>> iterator = entry.getValue().getKey().iterator(); iterator.hasNext(); ) {
                    queue = iterator.next();
                    if (queue == mQueue)
                        iterator.remove();
                }
            }


            notifyAll();
        }
        eventList.clear();
        broadcastList.clear();
        //System.out.println(m.getName()+" finish unregister");todo delete
    }

    @Override
    public Message awaitMessage(MicroService m) throws InterruptedException {

        synchronized (this) {
            //System.out.println(m.getName()+" start await");todo delete

            //If m's message queue is empty, wait, until notified.

            while (microServiceQueue.get(m).isEmpty())
                wait();
            //System.out.println(m.getName()+" finish await");todo

            return microServiceQueue.get(m).remove();

        }
    }

    private ConcurrentLinkedQueue<Message> roundRobin(LinkedList<ConcurrentLinkedQueue<Message>> list, ConcurrentLinkedQueue<Message> q) {

        synchronized (this) {
            //Create an iterator that points to q.
            Iterator<ConcurrentLinkedQueue<Message>> iterator = list.iterator();
            ConcurrentLinkedQueue<Message> queue;
            while (iterator.hasNext()) {
                queue = iterator.next();
                if (queue == q)
                    break;
            }
            if (iterator.hasNext())
                return iterator.next();
            else if (list.peekFirst() != null)
                return list.getFirst();
            else return null;
        }
    }


}
