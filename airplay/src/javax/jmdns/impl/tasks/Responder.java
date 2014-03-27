// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package javax.jmdns.impl.tasks;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.DNSIncoming;
import javax.jmdns.impl.DNSMessage;
import javax.jmdns.impl.DNSOutgoing;
import javax.jmdns.impl.DNSQuestion;
import javax.jmdns.impl.DNSRecord;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.ServiceInfoImpl;
import javax.jmdns.impl.DNSRecord.Pointer;
import javax.jmdns.impl.DNSRecord.Service;
import javax.jmdns.impl.DNSRecord.Text;
import javax.jmdns.impl.constants.DNSConstants;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;

import android.util.Log;

/**
 * The Responder sends a single answer for the specified service infos and for the host name.
 */
public class Responder extends DNSTask {
    static Logger             logger = Logger.getLogger(Responder.class.getName());

    /**
     *
     */
    private final DNSIncoming _in;
    
    private final InetAddress _addr;

    /**
     *
     */
    private final boolean     _unicast;

    public Responder(JmDNSImpl jmDNSImpl, DNSIncoming in, InetAddress addr, int port) {
        super(jmDNSImpl);
        this._in = in;
        this._addr = addr;
        this._unicast = (port != DNSConstants.MDNS_PORT);
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.DNSTask#getName()
     */
    @Override
    public String getName() {
        return "Responder(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return super.toString() + " incomming: " + _in;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.DNSTask#start(java.util.Timer)
     */
    @Override
    public void start(Timer timer) {
        // According to draft-cheshire-dnsext-multicastdns.txt chapter "7 Responding":
        // We respond immediately if we know for sure, that we are the only one who can respond to the query.
        // In all other cases, we respond within 20-120 ms.
        //
        // According to draft-cheshire-dnsext-multicastdns.txt chapter "6.2 Multi-Packet Known Answer Suppression":
        // We respond after 20-120 ms if the query is truncated.

        boolean iAmTheOnlyOne = true;
        for (DNSQuestion question : _in.getQuestions()) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest(this.getName() + "start() question=" + question);
            }
            iAmTheOnlyOne = question.iAmTheOnlyOne(this.getDns());
            if (!iAmTheOnlyOne) {
                break;
            }
        }
        int delay = (iAmTheOnlyOne && !_in.isTruncated()) ? 0 : DNSConstants.RESPONSE_MIN_WAIT_INTERVAL + JmDNSImpl.getRandom().nextInt(DNSConstants.RESPONSE_MAX_WAIT_INTERVAL - DNSConstants.RESPONSE_MIN_WAIT_INTERVAL + 1) - _in.elapseSinceArrival();
        if (delay < 0) {
            delay = 0;
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(this.getName() + "start() Responder chosen delay=" + delay);
        }
        if (!this.getDns().isCanceling() && !this.getDns().isCanceled()) {
            timer.schedule(this, delay);
        }
    }
    /*
    @Override
    public void run() {
        this.getDns().respondToQuery(_in);

        // We use these sets to prevent duplicate records
        Set<DNSQuestion> questions = new HashSet<DNSQuestion>();
        
        Set<DNSRecord> answers_Q = new HashSet<DNSRecord>();
        Set<DNSRecord> answers = new HashSet<DNSRecord>();
        
        Set<DNSRecord> answersQU = new HashSet<DNSRecord>();
        Set<DNSRecord> answersQUQ = new HashSet<DNSRecord>();
       // Log.d("JMDNS", "JMDNS [questions]:" + questions);
      //  Log.d("JMDNS", "JMDNS [answers]:" + answers);
        
        if (this.getDns().isAnnounced()) {
            try {
                // Answer questions
                for (DNSQuestion question : _in.getQuestions()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding to: " + question);
                    }
                    // for unicast responses the question must be included
                    if (_unicast) {
                        // out.addQuestion(q);
                    	//Log.d("JMDNS", "JMDNS [question]:" + question);
                        questions.add(question);
                    }
                    if(false == question.isUnique()){
                        for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
                            if ((serviceInfo != null) && ((ServiceInfoImpl) serviceInfo).isAnnounced()) {
                                if (question.getName().equalsIgnoreCase(serviceInfo.getQualifiedName()) || question.getName().equalsIgnoreCase(serviceInfo.getType())) {
                                    answers.addAll(this.getDns().getLocalHost().answers(DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL));
                                    
                                    Log.d("JMDNS", "JMDNS getType: " + question.getType());
                                    
                                    if(question.getRecordType() == DNSRecordType.TYPE_PTR){
                                    	
                                    	answers_Q.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                        answers.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                        answers.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    }
                                    
                                    if(question.getRecordType() == DNSRecordType.TYPE_SRV){
                                    	answers_Q.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                    	answers.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                        answers.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    }
                                    if(question.getRecordType() == DNSRecordType.TYPE_TXT){
                                    	answers_Q.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    	answers.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                        answers.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                    }
                                    
                                }
                            }
                        }
                    	//question.addAnswers(this.getDns(), answers);
                    }else{
                        for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
                            if ((serviceInfo != null) && ((ServiceInfoImpl) serviceInfo).isAnnounced()) {
                                if (question.getName().equalsIgnoreCase(serviceInfo.getQualifiedName()) || question.getName().equalsIgnoreCase(serviceInfo.getType())) {
                                	answersQU.addAll(this.getDns().getLocalHost().answers(DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL));
                                	
                                	Log.d("JMDNS", "JMDNS getType: " + question.getType());
                                	
                                	
                                    if(question.getRecordType() == DNSRecordType.TYPE_PTR){
                                    	answersQUQ.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                    	answersQU.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                    	answersQU.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    }
                                    
                                    if(question.getRecordType() == DNSRecordType.TYPE_SRV){
                                    	answersQUQ.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                    	answersQU.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                    	answersQU.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    }
                                    if(question.getRecordType() == DNSRecordType.TYPE_TXT){
                                    	answersQUQ.add(new Text(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getTextBytes()));
                                    	answersQU.add(new Pointer(serviceInfo.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getQualifiedName()));
                                    	answersQU.add(new Service(serviceInfo.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, serviceInfo.getPriority(), serviceInfo.getWeight(), serviceInfo.getPort(), this.getDns().getLocalHost().getName()));
                                    }
                                    
                                }
                        }
                    }
                }
                }
                
               // Log.d("JMDNS", "JMDNS [answers2]:" + answers);
              //  Log.d("JMDNS", "JMDNS [answersQU2]:" + answersQU);
                
                //Log.d("JMDNS", "JMDNS Responder [questions]: " + questions);

                // remove known answers, if the ttl is at least half of the correct value. (See Draft Cheshire chapter 7.1.).
                long now = System.currentTimeMillis();
                for (DNSRecord knownAnswer : _in.getAnswers()) {
                    if (knownAnswer.isStale(now)) {
                        answers.remove(knownAnswer);
                        answersQU.remove(knownAnswer);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer(this.getName() + "JmDNS Responder Known Answer Removed");
                        }
                    }
                }
              //  Log.d("JMDNS", "JMDNS [answers3]:" + answers);
             //   Log.d("JMDNS", "JMDNS [answersQU3]:" + answersQU);
                // respond if we have answers
                if (!answers.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    
              //      Log.d("JMDNS", "JMDNS [answers out]:" + out);
                    
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
                //    Log.d("JMDNS", "JMDNS [answers out2]:" + out);
                    for (DNSRecord answer : answers) {
                        if (answer != null) {
                           
                            out = this.addAdditionalAnswer(out, _in, answer);

                        }
                    }
                    for (DNSRecord answer : answers_Q) {
                        if (answer != null) {
                        	 out = this.addAnswer(out, answer);

                        }
                    }
              //      Log.d("JMDNS", "JMDNS [answers out3]:" + out);
                    if (!out.isEmpty()){
                        	this.getDns().send(out);
                        }
                    }
                   // Log.d("JMDNS", "JMDNS Responder [out]: " + out);
                    
                   // Log.d("JMDNS", "JMDNS Responder send");

                if (!answersQU.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    
               //     Log.d("JMDNS", "JMDNS [answersQU out]:" + out);
                    
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
               //     Log.d("JMDNS", "JMDNS [answersQU out2]:" + out);
                    for (DNSRecord answer : answersQU) {
                        if (answer != null) {
                        	out = this.addAdditionalAnswer(out, _in, answer);

                        }
                    }
                    
                    for (DNSRecord answer : answersQUQ) {
                        if (answer != null) {
                        	 out = this.addAnswer(out, answer);

                        }
                    }
               //     Log.d("JMDNS", "JMDNS [answersQU out3]:" + out);
                    if (!out.isEmpty()){
                        	this.getDns().send(out, _addr);
                        }
                    }

                
                
                // this.cancel();
            } catch (Throwable e) {
                logger.log(Level.WARNING, this.getName() + "run() exception ", e);
                this.getDns().close();
            }
        }
    }
    */
    /*
    @Override
    public void run() {
        this.getDns().respondToQuery(_in);

        // We use these sets to prevent duplicate records
        Set<DNSQuestion> questions = new HashSet<DNSQuestion>();
        
        
        if (this.getDns().isAnnounced()) {
            try {
                // Answer questions
                for (DNSQuestion question : _in.getQuestions()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding to: " + question);
                    }
                    // for unicast responses the question must be included
                    if (_unicast) {
                        questions.add(question);
                    }
                    
                    Set<DNSRecord> answers = new HashSet<DNSRecord>();
                    
                   // Log.d("JMDNS", "JMDNS [question]: " + question);
                    
                    question.addAnswers(this.getDns(), answers);
                    
                   // Log.d("JMDNS", "JMDNS [answers]: " + answers);
                    
                    
                    // remove known answers, if the ttl is at least half of the correct value. (See Draft Cheshire chapter 7.1.).
                    long now = System.currentTimeMillis();
                    for (DNSRecord knownAnswer : _in.getAnswers()) {
                        if (knownAnswer.isStale(now)) {
                            answers.remove(knownAnswer);
                            if (logger.isLoggable(Level.FINER)) {
                                logger.finer(this.getName() + "JmDNS Responder Known Answer Removed");
                            }
                        }
                    }
                    if (!answers.isEmpty()) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer(this.getName() + "run() JmDNS responding");
                        }
                        DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                        
                  //      Log.d("JMDNS", "JMDNS [answers out]:" + out);
                        
                        out.setId(_in.getId());
                        for (DNSQuestion question2 : questions) {
                            if (question2 != null) {
                                out = this.addQuestion(out, question2);
                            }
                        }
                        		if(false == question.isUnique()){
                                        for (DNSRecord answer : answers) {
                                            if (answer != null) {
                                                out = this.addAnswer(out, _in, answer);

                                           }
                                        }
                                        if (!out.isEmpty()){
                                        	this.getDns().send(out);
                                        }
                        		}else{
                                    for (DNSRecord answer : answers) {
                                        if (answer != null) {
                                            out = this.addAnswer(out, _in, answer);
                                       }
                                    }
                                    if (!out.isEmpty()){
                                    	
                                    	this.getDns().send(out, _addr);
                                    }
                        		}

                        }
                    

                }
                

                
                
                // this.cancel();
            } catch (Throwable e) {
                logger.log(Level.WARNING, this.getName() + "run() exception ", e);
                this.getDns().close();
            }
        }
    }
    */
   /*
    @Override
    public void run() {
        this.getDns().respondToQuery(_in);

        // We use these sets to prevent duplicate records
        Set<DNSQuestion> questions = new HashSet<DNSQuestion>();
        Set<DNSRecord> answers = new HashSet<DNSRecord>();
        
        Set<DNSRecord> answersQU = new HashSet<DNSRecord>();

       // Log.d("JMDNS", "JMDNS [questions]:" + questions);
      //  Log.d("JMDNS", "JMDNS [answers]:" + answers);
        
        if (this.getDns().isAnnounced()) {
            try {
                // Answer questions
                for (DNSQuestion question : _in.getQuestions()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding to: " + question);
                    }
                    // for unicast responses the question must be included
                    if (_unicast) {
                        // out.addQuestion(q);
                    	//Log.d("JMDNS", "JMDNS [question]:" + question);
                        questions.add(question);
                    }
                    Log.d("JMDNS", "JMDNS [question]:" + question);
                    
                    question.addAnswers(this.getDns(), answers);
                    question.addAnswers(this.getDns(), answersQU);
                }
                
               // Log.d("JMDNS", "JMDNS [answers2]:" + answers);
              //  Log.d("JMDNS", "JMDNS [answersQU2]:" + answersQU);
                
                //Log.d("JMDNS", "JMDNS Responder [questions]: " + questions);

                // remove known answers, if the ttl is at least half of the correct value. (See Draft Cheshire chapter 7.1.).
                long now = System.currentTimeMillis();
                for (DNSRecord knownAnswer : _in.getAnswers()) {
                    if (knownAnswer.isStale(now)) {
                        //answers.remove(knownAnswer);
                        //answersQU.remove(knownAnswer);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer(this.getName() + "JmDNS Responder Known Answer Removed");
                        }
                    }
                }
              //  Log.d("JMDNS", "JMDNS [answers3]:" + answers);
             //   Log.d("JMDNS", "JMDNS [answersQU3]:" + answersQU);
                // respond if we have answers
                if (!answers.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    
              //      Log.d("JMDNS", "JMDNS [answers out]:" + out);
                    
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
                //    Log.d("JMDNS", "JMDNS [answers out2]:" + out);
                    for (DNSRecord answer : answers) {
                        if (answer != null) {
                            out = this.addAnswer(out, _in, answer);

                        }
                    }
              //      Log.d("JMDNS", "JMDNS [answers out3]:" + out);
                    if (!out.isEmpty()){
                        	this.getDns().send(out);
                        }
                    }
                   // Log.d("JMDNS", "JMDNS Responder [out]: " + out);
                    
                   // Log.d("JMDNS", "JMDNS Responder send");

                if (!answersQU.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    
               //     Log.d("JMDNS", "JMDNS [answersQU out]:" + out);
                    
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
               //     Log.d("JMDNS", "JMDNS [answersQU out2]:" + out);
                    for (DNSRecord answer : answersQU) {
                        if (answer != null) {
                            out = this.addAnswer(out, _in, answer);

                        }
                    }
               //     Log.d("JMDNS", "JMDNS [answersQU out3]:" + out);
                    if (!out.isEmpty()){
                        	this.getDns().send(out, _addr);
                        }
                    }
                
                
                // this.cancel();
            } catch (Throwable e) {
                logger.log(Level.WARNING, this.getName() + "run() exception ", e);
                this.getDns().close();
            }
        }
    }
    */
   
    protected DNSOutgoing createOugoing2() {
        return new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
    }


    protected DNSOutgoing createOugoing1() {
        return new DNSOutgoing(DNSConstants.FLAGS_QR_QUERY);
    }
    
    protected DNSOutgoing buildOutgoingForInfo2(ServiceInfoImpl info, DNSOutgoing out, int ttl) throws IOException {
        DNSOutgoing newOut = out;
        for (DNSRecord answer : info.answers(DNSRecordClass.UNIQUE, ttl, this.getDns().getLocalHost())) {
            newOut = this.addAnswer(newOut, null, answer);
        }
        return newOut;
    }

    
    
    protected DNSOutgoing buildOutgoingForInfo1(ServiceInfoImpl info, DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        newOut = this.addQuestion(newOut, DNSQuestion.newQuestion(info.getQualifiedName(), DNSRecordType.TYPE_ANY, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
        // the "unique" flag should be not set here because these answers haven't been proven unique yet this means the record will not exactly match the announcement record
        newOut = this.addAuthoritativeAnswer(newOut, new DNSRecord.Service(info.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, info.getPriority(), info.getWeight(), info.getPort(), this.getDns().getLocalHost()
                .getName()));
        return newOut;
    }
    

    @Override
    public void run() {
        this.getDns().respondToQuery(_in);

        // We use these sets to prevent duplicate records
        Set<DNSQuestion> questions = new HashSet<DNSQuestion>();
        Set<DNSRecord> answers = new HashSet<DNSRecord>();

        Set<DNSRecord> answersQU = new HashSet<DNSRecord>();
        
        if (this.getDns().isAnnounced()) {
            try {
                // Answer questions
                for (DNSQuestion question : _in.getQuestions()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding to: " + question);
                    }
                    // for unicast responses the question must be included
                    if (_unicast) {
                        // out.addQuestion(q);
                        questions.add(question);
                    }

                    if(false == question.isUnique()){
                    	question.addAnswers(this.getDns(), answers);
                    	return;
                    }else{
                    	question.addAnswers(this.getDns(), answersQU);
                    }
                }

                // remove known answers, if the ttl is at least half of the correct value. (See Draft Cheshire chapter 7.1.).
                long now = System.currentTimeMillis();
                for (DNSRecord knownAnswer : _in.getAnswers()) {
                    if (knownAnswer.isStale(now)) {
                        answers.remove(knownAnswer);
                        answersQU.remove(knownAnswer);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer(this.getName() + "JmDNS Responder Known Answer Removed");
                        }
                    }
                }

                // respond if we have answers
                if (!answers.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
                    for (DNSRecord answer : answers) {
                        if (answer != null) {
                            out = this.addAnswer(out, _in, answer);

                        }
                    }
                    if (!out.isEmpty()){
                    	this.getDns().send(out);
                    	this.getDns().send(out, _addr);
                    
                    	//this.getDns().recoverState();
                    	//DNSOutgoing outAnnouncer = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
                    	
                    	//outAnnouncer = OutAnnouncer(outAnnouncer, 0);
                    	//if(!outAnnouncer.isEmpty()){
                    		//Thread.sleep(225);
                    	//	this.getDns().send(outAnnouncer);
                    	//	Thread.sleep(225);
                        //	this.getDns().send(outAnnouncer, _addr);
                    	//}
                    	
                    	//DNSOutgoing outAnnouncerZ = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
                    	
                    	//outAnnouncerZ = OutAnnouncer(outAnnouncerZ, DNSConstants.DNS_TTL);
                    	//if(!outAnnouncer.isEmpty()){
                    		//Thread.sleep(225);
                    	//	this.getDns().send(outAnnouncer);
                    	//	Thread.sleep(225);
                        //	this.getDns().send(outAnnouncer, _addr);
                    	//}
                    }

                
                }
                
                if (!answersQU.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() JmDNS responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
                    for (DNSRecord answer : answersQU) {
                        if (answer != null) {
                            out = this.addAnswer(out, _in, answer);

                        }
                    }
                    if (!out.isEmpty()){
                    	this.getDns().send(out, _addr);
                    	this.getDns().send(out);
                    	//this.getDns().registerFlush();
                    	//this.getDns().recoverState();
                    	//DNSOutgoing outAnnouncer = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
                    	
                    	//outAnnouncer = OutAnnouncer(outAnnouncer);
                    	//if(!outAnnouncer.isEmpty()){
                    	//	Thread.sleep(225);
                    	//	this.getDns().send(outAnnouncer);
                    	//	Thread.sleep(225);
                        //	this.getDns().send(outAnnouncer, _addr);
                    	//}
                    	
                    	//this.getDns().recoverState();
                    	//DNSOutgoing outAnnouncer = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
                    	
                    	//outAnnouncer = OutAnnouncer(outAnnouncer, 0);
                    	//if(!outAnnouncer.isEmpty()){
                    		//Thread.sleep(225);
                    	//	this.getDns().send(outAnnouncer);
                    	//	Thread.sleep(225);
                        //	this.getDns().send(outAnnouncer, _addr);
                    	//}
                    	
                    	//DNSOutgoing outAnnouncerZ = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
                    	
                    	//outAnnouncerZ = OutAnnouncer(outAnnouncerZ, DNSConstants.DNS_TTL);
                    	//if(!outAnnouncer.isEmpty()){
                    		//Thread.sleep(225);
                    	//	this.getDns().send(outAnnouncer);
                    	//	Thread.sleep(225);
                        //	this.getDns().send(outAnnouncer, _addr);
                    	//}
                    }

                
                }
                
                
                // this.cancel();
            } catch (Throwable e) {
                logger.log(Level.WARNING, this.getName() + "run() exception ", e);
                this.getDns().close();
            }
        }
    }

    private DNSOutgoing OutAnnouncer(DNSOutgoing out, int ttl){
    	DNSOutgoing newOut = out;
    	
        for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
            ServiceInfoImpl info = (ServiceInfoImpl) serviceInfo;

            if(info.getType().contains("airplay")){
                synchronized (info) {
                    try {
                    	newOut = this.buildOutgoingForInfo2(info, newOut, ttl);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}                 
                }
            }
            }
    	
		return newOut;
    	
    }
    
}