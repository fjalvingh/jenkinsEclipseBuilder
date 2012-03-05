package to.etc.prjbuilder.util;

public class Log {


	static public final void	log(String msg) {
		System.out.println("log: "+msg);
	}
	static public final void	exception(Throwable x, String msg) {
		System.out.println("exception: "+x+": "+msg);
		x.printStackTrace();
	}

}
