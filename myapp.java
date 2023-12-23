
/*
 * $Id: EchoServlet.java,v 1.5 2003/06/22 12:32:15 fukuda Exp $
 */
package org.mobicents.servlet.sip.example;

import java.util.*;
import java.io.IOException;

import javax.servlet.sip.SipServlet;	
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.ServletException;
import javax.servlet.sip.URI;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipFactory;

/**
 */
public class Myapp extends SipServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static private Map<String, String> RegistrarDB;
	//O atributo status possui os estados dos utilizadores e impede que ococrra uma chamada indicando se o user está ocupado ou em conferencia
	//(Esta funcionalidade não funciona pois a alteração de um utilizador para disponivel ocorre antes de uma chamada ou conferencia terminar) 
	static private Map<String, String> Status;
	static private SipFactory factory;
	private final String chosenDomain = "acme.pt";
	
	public Myapp() {
		super();
		RegistrarDB = new HashMap<String,String>();
		Status = new HashMap<String, String>();
		RegistrarDB.put("sip:chat@acme.pt", "sip:conf@127.0.0.1:5070"); //O servidor ao ser iniciado associa logo o aor ao ip:porto da conferencia
	}
	
	public void init() {
		factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
	}

	/**
        * Acts as a registrar and location service for REGISTER messages
        * @param  request The SIP message received by the AS 
        */
	protected void doRegister(SipServletRequest request) throws ServletException,
			IOException {

		String aor = getSIPuri(request.getHeader("To"));
		String contact = getSIPuriPort(request.getHeader("Contact"));
		String domain = aor.split("@")[1];
		String user = aor.split("@")[0];

		log("Domain being registered to: " + domain);
		//limita o registro somente para o dominio escolhido
		if (domain.equals(chosenDomain)){ 
			String expires = request.getHeader("Contact").split("=")[1];

			if (expires.equals("0")){ 
				//Se o user pretender o de-registro, ele é removido do hashmap
				log("O utilizador foi removido do dominio");
				RegistrarDB.remove(aor);
				httpMessage(request, 200);

			}else{
			//Caso contrário é adicionado ao hashmap
			RegistrarDB.put(aor, contact);
			Status.put(user, "Available");
			httpMessage(request, 200);
		
		}}else{
			httpMessage(request, 403);
			}
	}

	/**
        * Sends SIP replies to INVITE messages
        * - 300 if registred
        * - 404 if not registred
        * @param  request The SIP message received by the AS 
        */
	protected void doInvite(SipServletRequest request)
                  throws ServletException, IOException {

		String toDomain = request.getHeader("To").split("<")[1].split(">")[0].split("@")[1];
		String fromDomain = request.getHeader("From").split("<")[1].split(">")[0].split("@")[1];
		String aor = getSIPuri(request.getHeader("To"));
		String called = aor.split("@")[0];
		String caller = request.getHeader("From").split("<")[1].split(">")[0].split("@")[0];



		if(!fromDomain.equals(chosenDomain)){
			//Caso o user não pertença ao dominio, o serviço não é disponibilizado
			httpMessage(request, 403);

		//Primeiro o servidor analisa se o utilizador pertende realizar uma ligação direta ou indireta
		}else if(toDomain.equals(chosenDomain)){
	    	if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
				httpMessage(request, 404);
	    	}else{
				//Caso o utilizador destino esteja registado é procedida a chamada
				//Depois o servidor verifica se o utilizador está a realizar uma chamada com outro ou se quer entrar na conferencia, e altera o estado de forma apropriada
				if(called.equals("sip:chat")){
					Status.put(caller, "Conference");
					doCall(request, aor);
					Status.put(caller, "Available");
					}else{
					Status.put(called, "Occupied");
					Status.put(caller, "Occupied");
					doCall(request, aor);
					Status.put(called, "Available");
					Status.put(caller, "Available");

				}
			}		

		//}else if(request.getHeader("To").split("<")[1].split(">")[0].equals("sip:chat@acme.pt")){
		//		doCall(request, aor);

		//Caso o user pertenda realizar uma chamada direta, o servidor verifica se o utilizador origem e destino estão registados
		//(Intrepertamos pelo enunciado que ambos os utilizadores necessitavam de estar registados,
		// se somente o user origem necessita de ter o dominio no campo from, é só retirar o else if)
		}else if (partOfDomain(request)){
			if(Status.get(called).equals("Available")){
				Status.put(called, "Occupied");
				Status.put(caller, "Occupied");
				String to = request.getHeader("To");
				String to1 = to.split("<")[1].split(">")[0];
				Proxy proxy = request.getProxy();
				proxy.setRecordRoute(false);
				proxy.setSupervised(false);
				URI toContact = factory.createURI(to1);
				proxy.proxyTo(toContact);
				Status.put(called, "Available");
				Status.put(caller, "Available");
			}else{
				httpMessage(request, 486);
			}
		}else{
			httpMessage(request, 403);
		}

/*

		
		/*
		// Some logs to show the content of the Registrar database.
		log("INVITE (myapp):***");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		System.out.println(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("INVITE (myapp):***");
		

		String aor = getSIPuri(request.getHeader("To")); // Get the To AoR
	    if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
			SipServletResponse response; 
			response = request.createResponse(404);
			response.send();
	    } else {
			SipServletResponse response = request.createResponse(300);
			// Get the To AoR contact from the database and add it to the response 
			response.setHeader("Contact",RegistrarDB.get(aor));
			response.send();
		}
		SipServletResponse response = request.createResponse(404);
		response.send();
		
		String aor = getSIPuri(request.getHeader("To")); // Get the To AoR
		String domain = aor.substring(aor.indexOf("@")+1, aor.length());
		log(domain);
		if (domain.equals("acme.pt")) { // The To domain is the same as the server 
	    	if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
				SipServletResponse response; 
				response = request.createResponse(404);
				response.send();
	    	} else {
				Proxy proxy = request.getProxy();
				proxy.setRecordRoute(false);
				proxy.setSupervised(false);
				URI toContact = factory.createURI(RegistrarDB.get(aor));
				proxy.proxyTo(toContact);
			}			
		} else {
			Proxy proxy = request.getProxy();
			proxy.proxyTo(request.getRequestURI());
		}

		/*
	    if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
			SipServletResponse response; 
			response = request.createResponse(404);
			response.send();
	    } else {
			SipServletResponse response = request.createResponse(300);
			// Get the To AoR contact from the database and add it to the response 
			response.setHeader("Contact",RegistrarDB.get(aor));
			response.send();
		}
		*/

	}
	
	/**
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI 
        */
	protected String getSIPuri(String uri) {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		int indexCollon = f.indexOf(":", f.indexOf("@"));
		if (indexCollon != -1) {
			f = f.substring(0,indexCollon);
		}
		return f;
	}

	/**
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI and port 
        */
	protected String getSIPuriPort(String uri)  {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		return f;
	}

	protected void doCall (SipServletRequest request, String aor) throws ServletException, IOException{
		//Realiza a chamada, associando o aor ao ip:porto necessário
		Proxy proxy = request.getProxy();
		proxy.setRecordRoute(false);
		proxy.setSupervised(false);
		URI toContact = factory.createURI(RegistrarDB.get(aor));
		proxy.proxyTo(toContact);
	}		

	protected boolean partOfDomain (SipServletRequest request) throws ServletException, IOException{
		String caller = request.getHeader("Contact");
		String called = request.getHeader("To");
		String callerContact = caller.split("<")[1].split(">")[0];
		String calledContact = called.split("<")[1].split(">")[0];

		log("Called is at: " + calledContact + " and caller is at: " + callerContact);

		Iterator<Map.Entry<String,String>> contactMap = RegistrarDB.entrySet().iterator();
		boolean calledExists = false;
		boolean callerExists = false;

		//Percorre o hashmap RegistrarDB para verificar se ambos os utilizadores estão registados, retornando true se for o caso e false caso contrário
   		while (contactMap.hasNext()) {
       		Map.Entry<String,String> currentContact = (Map.Entry<String,String>)contactMap.next();
			if (currentContact.getKey().split("@")[1].equals(chosenDomain)){
				if (calledContact.equals(currentContact.getValue())){
					calledExists = true;
				}

				if (callerContact.equals(currentContact.getValue())){
					callerExists = true;
					}
			}
   		}
		if (callerExists && calledExists){
			return true;
		}
		return false;
	}	
		
	protected void httpMessage (SipServletRequest request, int status) throws ServletException, IOException{
		SipServletResponse response; 
		response = request.createResponse(status);
		response.send();
	}	


}
