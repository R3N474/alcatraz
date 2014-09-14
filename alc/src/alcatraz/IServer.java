package alcatraz;

import alcatraz.IClient;

import java.rmi.Remote;
import java.rmi.RemoteException;

/* 	Diese Interface hei�t Kommunikationsinterface. D.h. Folgendes muss die Interface erf�llen:
 * 		- Diese Interface 
 * 			- muss public sein
 * 		 	- muss java.rmi.Remote erben
 * 			- Jede deklarierte Methode muss java.rmi.RemoteException werfen.
 * 			- Parameter und Returnwerte der Methoden m�ssen serialisierbar sein.
 * 
 *  Durch Remote Library wei� der Kompiler, dass Server und Client in r�umlich getrennten Orten sind.
 *	Nur die Methoden angegeben, die remote vom Client zugegriffen werden k�nnen  
 */

public interface IServer extends Remote {
    
    void register(IClient ref) throws RemoteException, Exception;

    void signOut(IClient ref) throws RemoteException;

    void startAlcatraz(IClient ref) throws RemoteException, Exception;

}