package at.ac.fh_campuswien.its16.dsd.alcatraz.group5.client;

import at.ac.fh_campuswien.its16.dsd.alcatraz.group5.server.*;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/*  Diese Methoden sind aufrufbar vom Server	
 *  Diese Interface hei�t Kommunikationsinterface. D.h. Folgendes muss die Interface erf�llen:
 * 		- Remote interface (RI) muss public sein
 * 		- RI muss java.rmi.Remote erben
 * 		- Jede deklarierte Methode muss java.rmi.RemoteException werfen.
 * 		- Parameter und Returnwerte der Methoden m�ssen serialisierbar sein.
 * 
 *  Durch Remote Library wei� der Kompiler, dass Server und Client in r�umlich getrennten Orten sind.
 *	Nur die Methoden angegeben, die remote vom Client zugegriffen werden k�nnen  
 */

public interface IClient extends Remote, Serializable {
    String getName() throws RemoteException;

    void deliverReference(IClient[] ref, int playerNumber) throws RemoteException;

    void doMove(int playerId, int prisonerId, int rowOrCol, int row, int col, IClient ref) throws RemoteException;

    void receiveVoteCommit(IClient ref) throws RemoteException;

    void sendGlobalCommit(IClient ref) throws RemoteException;

    void receiveACK(IClient ref) throws RemoteException;

    void saveServerRef(IServer serverRef) throws RemoteException;
}