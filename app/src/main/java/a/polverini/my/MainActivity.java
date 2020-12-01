package a.polverini.my;

import android.app.*;
import android.content.*;
import android.net.http.*;
import android.os.*;
import android.preference.*;
import android.text.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.sql.*;
import java.text.*;
import java.util.*;
import javax.xml.parsers.*;
import org.h2.tools.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import android.*;
import android.content.pm.*;

/**
 * MyTiMeS
 * 
 */
public class MainActivity extends Activity 
{
	private static final String TAG = "MyTiMeS";
	private static final String DEBUG = "DEBUG: ";
	private static final String ERROR = "ERROR: ";
	
	private static boolean debug = false;
	private static boolean verbose = true;
	
	public static void debug(String s) {
		if(debug) {
			System.out.println(DEBUG+s);
		}
	}
	
	public static void verbose(String s) {
		if(verbose) {
			System.out.println(s);
		}
	}
	
	public static void error(Exception e) {
		System.out.println(ERROR+e.getClass().getSimpleName()+" "+e.getMessage());
	}
	
	private ProgressBar progress; 
	private WebView webView;
	private TextView logView;
	private Menu menu;
	private TMS tms;

	public ProgressBar getProgress() {
		return this.progress;
	}
	
	public Menu getMenu() {
		return this.menu;
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		setTitle(TAG+" build 000057 date 2020-12-01");
		
		logView = this.findViewById(R.id.LOGVIEW);
		TextHandler.instance(logView);
		System.out.println(getTitle());
		System.out.println("A.Polverini");
		
		progress = this.findViewById(R.id.PROGRESS);
		
		webView = this.findViewById(R.id.WEBVIEW);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setBuiltInZoomControls(true);
		webView.setWebViewClient(new TmsClient());
		webView.addJavascriptInterface(new TmsInterface(this), "TMS"); 
		HtmlHandler.instance(webView);
		
		preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext()); 
		preferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
					updatePreference(preferences, key);
				}
			});
			
			
		init(new File(Environment.getExternalStorageDirectory(), "tms").getAbsolutePath());
		
    }
	
	void init(String path) {
		debug(TAG+".init(path="+path+")");
		try {
			tms = new TMS(this);
			tms.setRoot(path);
			tms.start();
		} catch(Exception e) {
			error(e);
		}
	}
	
	@Override 
	protected void onDestroy() { 
		super.onDestroy(); 
	}

	@Override
	public void onBackPressed() {
		if (webView.canGoBack()) {
			webView.goBack();
		} else {
			super.onBackPressed();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		this.menu = menu;
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean log = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("log", true);
		menu.findItem(R.id.LOG).setChecked(log);
		findViewById(R.id.LOGVIEW).setVisibility(log ? View.VISIBLE : View.GONE);
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		try {
			switch (item.getItemId()) {
				case R.id.LOG:
					item.setChecked(!item.isChecked());
					PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit().putBoolean("log", item.isChecked()).apply();
					findViewById(R.id.LOGVIEW).setVisibility(item.isChecked() ? View.VISIBLE : View.GONE);
					return true;
				case R.id.PREFERENCES:
					startActivity(new Intent(this, MyPreferencesActivity.class));
					break;
				case R.id.LOGIN:
					AuthenticationDialog.instance(this).show();
					break;
				case R.id.DOWNLOAD:
					if(tms!=null) {
						tms.downloadAll();
					}
					break;
				case R.id.IMPORT:
					if(tms!=null) {
						tms.importAll();
					}
					break;
				case R.id.QUERY:
					if(tms!=null) {
						tms.queryAll();
					}
					break;
				case R.id.LOAD:
					if(tms!=null) {
						tms.loadIndex();
					}
					break;
				default:
					return super.onOptionsItemSelected(item);
			}
		} catch(Exception e) {
			error(e);
		}
		return true;
	}
	
	public static class HtmlHandler extends Handler {
		
		public static final int COMMAND = 0;
		public static final int MESSAGE = 1;
		
		public static final String COMMAND_CLEAR = "clear";
		public static final String COMMAND_LOAD  = "load";
		
		private static HtmlHandler instance = null;
		
		public static HtmlHandler instance(WebView webview) {
			instance = new HtmlHandler(webview);
			return instance;
		}
		
		public static void command(String s) {
			debug("command(\""+s+"\");");
			instance.obtainMessage(COMMAND, s).sendToTarget();
		}

		public static void print(String fmt, Object... args) {
			instance.obtainMessage(MESSAGE, String.format(fmt, args)).sendToTarget();
		}
		
		private WebView view;
		private StringBuffer buffer = new StringBuffer();

		public HtmlHandler(WebView view) {
			super(Looper.getMainLooper());
			this.view = view;
		}

		@Override
		public void handleMessage(Message message) {
			switch(message.what) {
				case COMMAND:
					if(message.obj instanceof String) {
						String[] args = ((String)message.obj).split(" ");
						switch (args[0]) {
							case COMMAND_CLEAR:
								if(buffer.length()>0) {
									buffer.delete(0,buffer.length());
									view.loadData(buffer.toString(), "text/html", "UTF-8");
								}
								break;
							case COMMAND_LOAD:
								if(args.length>1) {
									try {
										debug("DEBUG: webview.loadUrl(\""+args[1]+"\");");
										view.loadUrl(args[1]);
									} catch(Exception e) {
										error(e);
									}
								}
								break;
							default:
								break;
						}
					}
					break;
				case MESSAGE:
					if(message.obj instanceof String) {
						buffer.append((String)message.obj);
						view.loadData(buffer.toString(), "text/html", "UTF-8");
					}
					break;
				default:
					break;
			}
		}
	}
	
	private static class TextHandler extends android.os.Handler
	{
		private final int MESSAGE = 100;
		private final TextView text;

		public static TextHandler instance(TextView textView) {
			try {
				final TextHandler textHandler = new TextHandler(textView);
				System.setOut(new PrintStream(System.out) {

						@Override
						public PrintStream printf(String format, Object... args) {
							textHandler.print(format, args);
							return this;
						}

						@Override
						public void print(String s) {
							printf(s);
						}

						@Override
						public void println(String s) {
							printf(s+"\n");
						}

						@Override
						public void println() {
							printf("\n");
						}
					});
				return textHandler;
			} catch (Exception e) {
				error(e);
			}
			return null;
		}

		public TextHandler(TextView text) {
			super(Looper.getMainLooper());
			this.text = text;
		}

		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
				case MESSAGE:
					try {
						String s = message.getData().getString("text");
						if(s.startsWith(DEBUG)) {
							text.append(Html.fromHtml("<font color=\"blue\">" + s.replaceAll("\n","<br>") + "</font>"));
							return;
						}
						if(s.startsWith(ERROR)) {
							text.append(Html.fromHtml("<font color=\"red\">" + s.replaceAll("\n","<br>") + "</font>"));
							return;
						}
						text.append(s);
					} catch(Exception e) {
						text.append(ERROR+e.getClass().getSimpleName()+" "+e.getMessage());
					}
					break;
				default:
					super.handleMessage(message);
					break;
			}
		}

		public void print(String format, Object... args) {
			Message message = obtainMessage(MESSAGE);
			Bundle data = new Bundle();
			data.putString("text", String.format(format, args));
			message.setData(data);
			message.sendToTarget();
		}

	}
	
	public class TmsInterface {

		private Context context;

		public TmsInterface(Context c) {
			context = c;
		}

		@JavascriptInterface
		public void toast(String s) {
			Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
		}

	}

	public class TmsClient extends WebViewClient {
		
		public TmsClient() {
			super();
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
		
		@Override
		public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {      
			super.onReceivedClientCertRequest(view, request);
		}
		
		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			super.onReceivedSslError(view, handler, error);
		}
		
	}

	static class EGSCC {
		
		static class CSDE {
			
			public static final String HOME = "https://csde.esa.int/confluence/pages/index.action#all-updates";
			
		}
		
		/**
		 * gives the reference to the EGS-CC GIT server 
		 * 
		 * TODO: GIT access does not work on https://code.egscc.dev
		 */
		@Deprecated
		static class GIT {

			public static final String HOME = "https://code.egscc.dev/projects/EGS-CC";
			public static final String R1_RELEASE_BASELINE = "https://alberto.polverini:Sabr1na$@code.egscc.dev/projects/EGS-CC/repos/a-egscc-releases/raw/sde/baselines/r1_release_baseline.txt?at=refs%2Fheads%2Fmaster";
			
		}
		
		public static String RELEASE = "r1_release_baseline.txt";

	}
	
	static class TMS {
		
		private static final String TAG = "TMS";

		private Context context;
		
		private File root;
		private File rootsql;
		private File rootxml;
		private File roothtml;
		private File rootdb;
		
		private DB db;
		private XML xml;
		
		private File releasefile;
		
		private Map<String, String> aliases = new HashMap<>();
		private Map<String, String> releases = new HashMap<>();
		private Map<String, Specification> specifications = new HashMap <>();
		private Map<String, Results> testResults = new HashMap <>();
		
		class TaskQueue extends LinkedList<GenericTask> {
			
			private static final String TAG = "TaskQueue";
			
			@Override
			public boolean add(GenericTask task) {
				debug(TAG+".add()");
				if(isEmpty()) {
					boolean ok = super.add(task);
					task.start();
					return ok;
				} else {
					return super.add(task);
				}
			}
			
			public void next() {
				debug(TAG+".next()");
				this.poll();
				if(!isEmpty()) {
					GenericTask task = peek();
					task.start(); 
				}
			}
		}
		
		private TaskQueue queue = new TaskQueue();
		
		public TMS(Context context) {
			this.context = context;
			try {
				db = new DB();
				xml = new XML();
			} catch (Exception e) {
				error(e);
			}
		}

		public void downloadAll() {
			debug(TAG+".downloadAll()");
			String[] components = releases.keySet().toArray(new String[releases.size()]);
			queue.add(new DownloadTask(components));
		}
		
		public void importAll() {
			debug(TAG+".importAll()");
			String[] components = releases.keySet().toArray(new String[releases.size()]);
			queue.add(new ImportTask(components));
		}
		
		public void queryAll() {
			debug(TAG+".queryAll()");
			String[] components = releases.keySet().toArray(new String[releases.size()]);
			queue.add(new QueryTask(components));
		}
		
		public void loadIndex() {
			debug(TAG+".loadAll()");
			String[] components = releases.keySet().toArray(new String[releases.size()]);
			Arrays.sort(components); 
			queue.add(new LoadTask(components));
		}
		
		public void loadAlias() {
			debug(TAG+".loadAlias()");
			queue.add(new LoadAliasTask(new File(getRoot(), "alias.txt").getAbsolutePath()));
		}
		
		public void loadRelease() {
			debug(TAG+".loadRelease()");
			queue.add(new LoadReleaseTask(new File(getRoot(), EGSCC.RELEASE).getAbsolutePath()));
		}
		
		public void init() {
			debug(TAG+".init()");
			queue.add(new InitTask());
		}
		
		public File getSQLroot() {
			return this.rootsql;
		}
		
		public File getXMLroot() {
			return this.rootxml;
		}
		
		public File getHTMLroot() {
			return this.roothtml;
		}
		
		public File getDBroot() {
			return this.roothtml;
		}
		
		public File getRoot() {
			return this.root;
		}
		
		public void setRoot(String path) {
			debug(TAG+".setRoot(path=\""+path+"\")");
			try {
				root = create(new File(path));
				if(root.exists()) {
					System.out.println("root="+root.getAbsolutePath());
					PreferenceManager.getDefaultSharedPreferences(context).edit().putString("root", path).apply();
					rootsql  = create(new File(path, "sql"));
					rootxml  = create(new File(path, "xml"));
					roothtml = create(new File(path, "html"));
					rootdb   = create(new File(path, "db"));
				} else {
					System.out.println("no root="+root.getAbsolutePath());
				}
			} catch(Exception e) {
				error(e);
			}
		}

		private File create(File file) {
			debug(TAG+".create(file=\""+file.getAbsolutePath()+"\")");
			if(!file.exists()) {
				debug("  mkdirs");
				file.mkdirs();
				if(!file.exists()) {
					System.out.println("no file="+file.getAbsolutePath());
				}
			} else {
				debug("  exists");
			}
			return file.exists() ? file : null;
		}
		
		public void start() {
			debug(TAG+".start()");
			try {
				loadAlias();
				loadRelease();
				init();
			} catch (Exception e) {
				error(e);
			}
		}
		
		private abstract class GenericTask extends AsyncTask<String, Integer, String> 
		{
			private static final String TAG = "GenericTask";

			private ProgressBar progress;

			private String[] args;
			
			public GenericTask(String... args) {
				this.args = args;
				debug(TAG);
			}
			
			public void start() {
				debug(TAG+".start()");
				execute(args);
			}
			
			@Override
			protected void onPreExecute() {
				debug(TAG+".onPreExecute()");
				this.progress = ((MainActivity)context).getProgress();
				//progress.setVisibility(ProgressBar.VISIBLE);
				progress.setMax(100);
				progress.setProgress(0);
				super.onPreExecute();
			}

			@Override
			protected void onPostExecute(String result) {
				debug(TAG+".onPostExecute()");
				//progress.setVisibility(ProgressBar.INVISIBLE);
				progress.setProgress(0);
				super.onPostExecute(result);
				queue.next();
			}

			@Override
			protected void onCancelled() {
				debug(TAG+".onCancelled()");
				//progress.setVisibility(ProgressBar.INVISIBLE);
				super.onCancelled();
			}

			@Override
			protected void onProgressUpdate(Integer... status) {
				debug(TAG+".onProgressUpdate("+status[0]+")");
				super.onProgressUpdate(status);
				progress.setProgress(status[0]);
			}

		}
		
		private class LoadReleaseTask extends GenericTask 
		{
			private static final String TAG = "LoadReleaseTask";
			
			public LoadReleaseTask(String... args) {
				super(args);
				debug(TAG);
			}
			
			@Override
			protected String doInBackground(String... args) {
				debug(TAG+".doInBackground()");
				try {
					File file = new File(args[0]);
					if(!file.exists()) {
						error(new FileNotFoundException(file.getAbsolutePath()));
						return "failed";
					}
					long length = file.length();
					long offset = 0;
					BufferedReader reader = new BufferedReader(new FileReader(file)); 
					String line;  
					while((line = reader.readLine())!=null) {
						offset += line.length();
						publishProgress((int)(100*offset/length));
						if(line.startsWith("#")) continue;
						if(line.trim().isEmpty()) continue;
						debug(line);
						String[] list = line.split(" ");
						releases.put(list[0].trim(), list[1].trim());
					}  
					reader.close();
					releasefile = file;
					return "ok";
				} catch(Exception e) {
					error(e);
					return "failed";
				}
			}
		}
		
		private class LoadAliasTask extends GenericTask 
		{
			private static final String TAG = "LoadAliasTask";
			
			public LoadAliasTask(String... args) {
				super(args);
				debug(TAG);
			}
			
			@Override
			protected String doInBackground(String... args) {
				debug(TAG+".doInBackground()");
				try {
					File file = new File(args[0]);
					if(!file.exists()) {
						error(new FileNotFoundException(file.getAbsolutePath()));
						return "failed";
					}

					long length = file.length();
					long offset = 0;

					BufferedReader reader = new BufferedReader(new FileReader(file)); 
					String line;  
					while((line = reader.readLine())!=null) {
						offset += line.length();
						publishProgress((int)(100*offset/length));
						if(line.startsWith("#")) continue;
						if(line.trim().isEmpty()) continue;
						debug(line);
						String[] list = line.split(" ");
						aliases.put(list[0].trim(), list[1].trim());
					}  
					reader.close();
					return "ok";
				} catch(Exception e) {
					error(e);
					return "failed";
				}
			}
		}
		
		private class InitTask extends GenericTask 
		{
			private static final String TAG = "InitTask";

			public InitTask(String... args) {
				super(args);
				debug(TAG);
			}

			@Override
			protected String doInBackground(String... args) {
				debug(TAG+".doInBackground()");
				try {
					// downloadAll();
					importAll();
					queryAll();
					loadIndex();
					return "ok";
				} catch(Exception e) {
					error(e);
					return "failed";
				}
			}
		}
		
		private class DownloadTask extends GenericTask 
		{
			private static final String TAG = "DownloadTask";
			
			public DownloadTask(String... args) {
				super(args);
			}
			
			@Override
			protected String doInBackground(String... args) {
				if(db==null || xml==null) {
					return "failed";
				}
				
				for(int i=0;i<args.length;i++) {
					
					String component = args[i];
					String branch = releases.containsKey(component) ? releases.get(component) : "dev";

					verbose(TAG+" "+component+" "+branch);

					try {
						db.downloadSQL(component, branch);
					} catch (Exception e) {
						error(e);
					}

					try {
						xml.download(component);
					} catch (Exception e) {
						error(e);
					}
					
					publishProgress(100*i/args.length);
				}
				
				return "ok";
			}
		}
		
		private class ImportTask extends GenericTask
		{
			private static final String TAG = "ImportTask";
			
			public ImportTask(String... args) {
				super(args);
				verbose(TAG);
			}
			
			@Override
			protected String doInBackground(String... args) {
				if(db==null) {
					error(new Exception(TAG+" missing database"));
					return "fail";
				}
				try {
					verbose(TAG+".doInBackground()");
					for(int i=0;i<args.length;i++) {
						try {
							String name = args[i];
							if(db.sqlexists(name) && !db.updated(name)) {
								debug(TAG+" "+name+" ... ");
								db.importSQL(name);
							}
						} catch(Exception e) {
							error(e);
						}
						publishProgress(100*i/args.length);
					}
					return "ok";
				} catch(Exception e) {
					error(e);
					return "fail";
				}
			}
		}
		
		private class QueryTask extends GenericTask
		{
			private static final String TAG = "QueryTask";
			
			public QueryTask(String... args) {
				super(args);
				debug(TAG);
			}
			
			@Override
			protected String doInBackground(String... args)
			{
				verbose(TAG+".doInBackground()");

				File indexfile = new File(roothtml, "index.html");
				if(indexfile.lastModified()>=releasefile.lastModified()) {
					verbose(TAG+" update index");
					indexfile = writeIndex(args);
				}
				if(indexfile!=null) {
					HtmlHandler.command("load file://"+indexfile.getAbsolutePath());
				}

				for(int i = 0; i<args.length; i++) {
					
					String name = args[i];
					
					verbose(TAG+" "+name+" ... ");
					
					if(db.dbexists(name)) {
						try {
							Specification specification = new Specification();
							specification.query(name);
							specifications.put(name, specification);
						} catch (Exception e) {
							error(e);
						}
					}
					
					if(xml.exists(name)) {
						try {
							Results results = new Results();
							results.readXML(name);
							testResults.put(name, results);
						} catch (Exception e) {
							error(e);
						}
					}
					
					if(true) {
						try {
							if(specifications.containsKey(name)) {
								Item tree = populate(name);
								writeTree(name, tree);
								writeRequirements(name, tree);
								writeDetails(name, tree);
								if(testResults.containsKey(name)) {
									writeResults(name, tree);
								}
							}
						} catch(Exception e) {
							error(e);
						}
					}

					publishProgress(100*i/args.length);
				}
				return "ok";
			}
			
			private File writeIndex(String[] names) {
				File file = null;
				try {
					file = new File(roothtml, "index.html");
					MyFileWriter html = null;
					try {
						html = new MyFileWriter(file);
						html.println("<!DOCTYPE html>");
						html.println("<html>");
						html.println("  <head>");
						html.println("    <title>index</title>");
						html.println("  </head>");
						html.println("  <body>");
						html.println("    <ul>");
						for(String name : names) {
							File htmlfile = new File(roothtml, name+".html");
							if(htmlfile.exists()) {
								html.println("      <li><a href='"+htmlfile.getAbsolutePath()+"'>"+name+"</a></li>");
							} else {
								html.println("      <li>"+name+"</li>");
							}
						}
						html.println("    </ul>");
						html.println("  </body>");
						html.println("</html>");
					} catch (Exception e) {
						error(e);
					} finally {
						html.close();
					}
				} catch (Exception e) {
					error(e);
				}
				return file;
			}
			
			private void writeResults(String name, MainActivity.Item tree)
			{
				try {
					File file = new File(roothtml, name+"-results.html");
					MyFileWriter html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("  <head>");
					html.println("    <title>"+name+" - details</title>");
					html.println("  </head>");
					html.println("  <body>");

					Item projects = tree.getChild("projects");
					for(Item project : projects.getChildren()) {
						html.println("    <H1 id='project-%s'>project: %s</H1>", project.getName(), project.getName());

						Item results = project.getChild("results");
						if(results!=null) {
							html.println("<H2>RESULTS</H2>");

							Item scenarioExecutions = results.getChild("scenarioexecutions");
							for(Item scenarioExecution : scenarioExecutions.getChildren()) {
								Properties scenarioExecutionProperties = scenarioExecution.getProperties();
								html.println("<H3 id='scenarioexecution-%s'>%s scenario: %s</H3>",
											 scenarioExecutionProperties.getProperty("START"),
											 scenarioExecutionProperties.getProperty("START"),
											 scenarioExecutionProperties.getProperty("SCENARIO"));

								html.println("<P>%s rc-%s</P>", scenarioExecutionProperties.getProperty("PROJECT"), scenarioExecutionProperties.getProperty("VERSION"));
								html.println("<P><B>%s:</B> %s</P>", "START", scenarioExecutionProperties.getProperty("START"));
								html.println("<P><B>%s:</B> %s</P>", "END", scenarioExecutionProperties.getProperty("END"));

								String info = scenarioExecutionProperties.getProperty("INFO");
								if(info!=null && !info.isEmpty()) {
									html.println("<P><B>%s:</B> %s</P>", "INFO", info);
								}

								Item procedureExecutions = scenarioExecution.getChild("procedureexecutions");
								for(Item procedureExecution : procedureExecutions.getChildren()) {
									Properties procedureExecutionProperties = procedureExecution.getProperties();

									html.println("<H4 id='procedureexecution-%s'>%s procedure: %s</H4>",
												 procedureExecutionProperties.getProperty("START"),
												 procedureExecutionProperties.getProperty("START"),
												 procedureExecutionProperties.getProperty("PROCEDURE"));

									html.println("<P><B>%s:</B> %s</P>", "START", procedureExecutionProperties.getProperty("START"));
									html.println("<P><B>%s:</B> %s</P>", "END", procedureExecutionProperties.getProperty("END"));

									String summary = procedureExecutionProperties.getProperty("SUMMARY");
									html.println("<P><B>%s:</B> %s</P>", "SUMMARY", summary);

									String comment = procedureExecutionProperties.getProperty("COMMENT");
									if(comment!=null && !comment.isEmpty()) {
										html.println("<P><B>%s:</B> %s</P>", "COMMENT", comment);
									}

									Item stepExecutions = procedureExecution.getChild("stepexecutions");
									if(stepExecutions!=null) {
										html.println("<P><B>STEPS:</B></p>");
										html.println("<UL>");
										List<Item> stepsList = stepExecutions.getChildren();
										Collections.sort(stepsList, new Comparator<Item>() { 
												@Override 
												public int compare(Item a, Item b) { 
													Integer ia = Integer.parseInt(a.getProperty("STEP"));
													Integer ib = Integer.parseInt(b.getProperty("STEP"));
													return ia.compareTo(ib); 
												} 
											});
										for (Item stepExecution : stepsList)
										{
											Properties stepExecutionProperties = stepExecution.getProperties();
											html.println("<LI>step: %s = %s</LI>",
														 stepExecutionProperties.getProperty("STEP"),
														 stepExecutionProperties.getProperty("EXECUTED"));
										}
										html.println("</UL>");
									}

									Item verdicts = procedureExecution.getChild("verdicts");
									if(verdicts!=null) {
										html.println("<P><B>VERDICTS:</B></P>");
										html.println("<UL>");
										List<Item> verdictsList = verdicts.getChildren();
										Collections.sort(verdictsList, new Comparator<Item>() { 
												@Override 
												public int compare(Item a, Item b) { 
													return a.getProperty("TESTCASE").compareTo(b.getProperty("TESTCASE")); 
												} 
											});
										for(Item verdict : verdictsList) {
											Properties verdictProperties = verdict.getProperties();
											html.println("<LI>verdict: %s = %s</LI>",
														 verdictProperties.getProperty("TESTCASE"),
														 verdictProperties.getProperty("VERDICT"));
										}
										html.println("</UL>");
									}
								}
							}
						}
					}
					html.println("  </body>");
					html.println("</html>");
					html.close();
				} catch (FileNotFoundException e) {
					error(e);
				}
			}

			private String multiline(String s, boolean must) {
				if(s!=null && !s.isEmpty()) {
					s = s.replaceAll("\n", "<br>");
				} else {
					if(must) {
						s = "<span style='color:red'>(TBD)</span>";
					} else {
						s = "";
					}
				}
				return s;
			}

			private void writeDetails(String param, MainActivity.Item tree)
			{
				try {
					File file = new File(getHTMLroot(), param+"-details.html");
					MyFileWriter html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("  <head>");
					html.println("    <title>"+param+" - details</title>");
					html.println("  </head>");
					html.println("  <body>");
					Item projects = tree.getChild("projects");
					for(Item project : projects.getChildren()) {
						html.println("<H1 id='project-%s'>project: %s</H1>", project.getName(), project.getName());

						html.println("<H2>TEST SPECIFICATION</H2>");

						Item testareas = project.getChild("testareas");
						if(testareas!=null) {
							html.println("<P><B>TEST AREAS:</B></P>");
							html.println("<OL>");
							for(Item testarea : testareas.getChildren()) {
								html.println("<LI>testarea: <A HREF='#testarea-%s'>%s</A> = %s</LI>", testarea.getName(), testarea.getName(), testarea.getProperty("TITLE"));
							}
							html.println("</OL>");
						}

						for(Item testarea : testareas.getChildren()) {
							html.println("<H3 id='testarea-%s'>testarea: %s = %s</H3>", testarea.getName(), testarea.getName(), testarea.getProperties().getProperty("TITLE"));
							Properties testareaProperties = testarea.getProperties();

							html.println("<p><b>%s:</b> %s</p>", "DESCRIPTION", multiline(testareaProperties.getProperty("DESCRIPTION"), true));

							Item features = testarea.getChild("features");
							if(features!=null) {
								html.println("<P><B>FEATURES:</B></P>");
								html.println("<OL>");
								for(Item feature : features.getChildren()) {
									html.println("<LI>feature: <A HREF='#feature-%s'>%s</A> = %s</LI>", feature.getName(), feature.getName(), feature.getProperty("TITLE"));
								}
								html.println("</OL>");
							}

							for(Item feature : features.getChildren()) {
								html.println("<H4 id='feature-%s'>feature: %s = %s</H4>", feature.getName(), feature.getName(), feature.getProperties().getProperty("TITLE"));
								Properties featureProperties = feature.getProperties();

								html.println("<p><b>%s:</b> %s</p>", "DESCRIPTION", multiline(featureProperties.getProperty("DESCRIPTION"), true));

								Item testcases = feature.getChild("testcases");
								if(testcases!=null) {
									html.println("<P><B>TEST CASES:</B></P>");
									html.println("<OL>");
									for(Item testcase : testcases.getChildren()) {
										html.println("<LI>testcase: <A HREF='#testcase-%s'>%s</A> = %s</LI>", testcase.getName(), testcase.getName(), testcase.getProperty("TITLE"));
									}
									html.println("</OL>");
								}

								for(Item testcase : testcases.getChildren()) {
									html.println("<H5 id='testcase-%s'>testcase: %s = %s</H4>", testcase.getName(), testcase.getName(), testcase.getProperties().getProperty("TITLE"));
									Properties testcaseProperties = testcase.getProperties();

									html.println("<p><b>%s:</b> %s</p>", "SPECIFICATION", multiline(testcaseProperties.getProperty("SPECIFICATION"), true));
									html.println("<p><b>%s:</b> %s</p>", "SCOPE", multiline(testcaseProperties.getProperty("SCOPE"), false));
									html.println("<p><b>%s:</b> %s</p>", "PASS/FAIL", multiline(testcaseProperties.getProperty("CRITERIA"), true));

									Item requirements = testcase.getChild("requirements");
									if(requirements!=null) {
										html.println("<P><B>REQUIREMENTS:</B></P>");
										html.println("<OL>");
										for(Item requirement : requirements.getChildren()) {
											String title = specifications.get(param).get(Specification.REQUIREMENT, requirement.getName()).getProperty("NAME");
											html.println("<LI>requirement:  <a href='%s.html#requirement-%s'>%s</a> = %s</LI>", html.getName().replaceFirst("-details", "-requirements"), requirement.getName(), requirement.getName(), title);
										}
										html.println("</OL>");
									}
								}
							}
						}

						html.println("<H2>TEST DESIGN</H2>");

						Item scenarios = project.getChild("scenarios");
						if(scenarios!=null) {
							html.println("<P><B>SCENARIOS:</B></P>");
							html.println("<OL>");
							for(Item scenario : scenarios.getChildren()) {
								html.println("<LI>scenario: <A HREF='#scenario-%s'>%s</A> = %s</LI>", scenario.getName(), scenario.getName(), scenario.getProperty("TITLE"));
							}
							html.println("</OL>");
						}

						for(Item scenario : scenarios.getChildren()) {
							html.println("<H3 id='scenario-%s'>scenario: %s = %s</H3>", scenario.getName(), scenario.getName(), scenario.getProperties().getProperty("TITLE"));
							Properties scenarioProperties = scenario.getProperties();

							String execute = "";
							if(scenarioProperties.getProperty("SCENARIOTYPE").equals("MANUAL")) {
								writeManualScenario(param, scenario);
								execute = String.format("<a href='%s-%s.html'>[%s]</a>", html.getName().replaceFirst("-details", "-scenario"), scenario.getName(), "EXECUTE" );
							}

							html.println("<p><b>%s:</b> %s %s</p>", "TYPE", scenarioProperties.getProperty("SCENARIOTYPE"), execute);

							html.println("<p><b>%s:</b> %s</p>", "DESCRIPTION", multiline(scenarioProperties.getProperty("DESCRIPTION"), true));

							if(scenarioProperties.containsKey("TESTAREA_ID")) {
								String testareaID = scenarioProperties.getProperty("TESTAREA_ID");
								String title = specifications.get(param).get(Specification.TESTAREAS, testareaID).getProperty("TITLE");
								html.println("<p><b>%s:</b> <a href='#testarea-%s'>%s</a> = %s</p>", "TESTAREA", testareaID, testareaID, title);
							}

							Item procedures = scenario.getChild("procedures");
							if(procedures!=null) {
								html.println("<P><B>PROCEDURES:</B></P>");
								html.println("<OL>");
								for(Item procedure : procedures.getChildren()) {
									html.println("<LI>procedure: <A HREF='#procedure-%s'>%s</A> = %s</LI>", procedure.getName(), procedure.getName(), procedure.getProperty("TITLE"));
								}
								html.println("</OL>");
							}

							Item scenarioDeployments = scenario.getChild("deployments");
							if(scenarioDeployments!=null) {
								html.println("<P><B>DEPLOYMENTS:</B></P>");
								html.println("<OL>");
								for(Item deployment : scenarioDeployments.getChildren()) {
									html.println("<LI>deployment: %s</LI>", deployment.getName());
								}
								html.println("</OL>");
							}

							Item scenarioMeasurements = scenario.getChild("measurements");
							if(scenarioMeasurements!=null) {
								html.println("<P><B>MEASUREMENTS:</B></P>");
								html.println("<OL>");
								for(Item measurement : scenarioMeasurements.getChildren()) {
									html.println("<LI>measurement: %s</LI>", measurement.getName());
								}
								html.println("</OL>");
							}

							for(Item procedure : procedures.getChildren()) {
								html.println("<H4 id='procedure-%s'>procedure: %s = %s</H4>", procedure.getName(), procedure.getName(), procedure.getProperties().getProperty("TITLE"));

								Properties procedureProperties = procedure.getProperties();

								html.println("<P><B>%s:</B> %s</P>", "TYPE", procedureProperties.getProperty("PROCEDURE_TYPE"));
								html.println("<P><B>%s:</B> %s</P>", "DESCRIPTION", multiline(procedureProperties.getProperty("DESCRIPTION"), true));

								try {
									if(procedureProperties.get("PROCEDURE_TYPE").equals("MANUAL")) {
										Item steps = procedure.getChild("steps");
										if(steps!=null) {
											html.println("<P><B>STEPS:</B></P>");
											html.println("<OL>");
											for(Item step : steps.getChildren()) {
												Properties stepProperties = step.getProperties();
												html.println("<LI>%s</LI>", multiline(stepProperties.getProperty("ACTION"), true));
												html.println("<p><b>%s:</b> %s</p>", "EXPECTED", multiline(stepProperties.getProperty("EXPECTEDRESULTS"), true));
												String comments = stepProperties.getProperty("COMMENTS");
												if(comments!=null && !comments.trim().isEmpty()) {
													html.println("<p><b>%s:</b> %s</p>", "COMMENTS", multiline(comments, false));
												}
											}
											html.println("</OL>");
										}
									}
								} catch(Exception e) {
									error(e);
								}

								Item testcases = procedure.getChild("testcases");
								if(testcases!=null) {
									html.println("<P><B>TEST CASES:</B></P>");
									html.println("<OL>");
									for(Item testcase : testcases.getChildren()) {
										String title = specifications.get(param).get(Specification.TESTCASES, testcase.getName()).getProperty("TITLE");
										html.println("<LI>testcase:  <A HREF='#testcase-%s'>%s</A> = %s</LI>", testcase.getName(), testcase.getName(), title);
									}
									html.println("</OL>");
								}
							}
						}
					}
					html.println("  </body>");
					html.println("</html>");
					html.close();
				} catch (FileNotFoundException e) {
					error(e);
				}
			}

			public String htmlFormScript() {
				String html = "";
				html+="<script type=\"text/javascript\">";
				html+="function validateForm() {";
				html+="  var x = document.forms[\"manual-scenario\"][\"fname\"].value;";
				html+="  if (x == \"\") {";
				html+="    alert(\"Name must be filled-in\");";
				html+="    return false;";
				html+="  }";
				html+="}";
				html+="</script>";
				return html;
			}

			public String jsWriteFile() {
				String html = "";
				html+="<script type=\"text/javascript\">";
				html+="function writeFile() {";
				html+="  var fh = fopen('/storage/emulated/0/egscc/html/amy.txt', 3);";
				html+="  if(fh!=-1) {";
				html+="    var str = 'Some text goes here...';";
				html+="    fwrite(fh, str);";
				html+="    fclose(fh);";
				html+="  }";
				html+="}";
				html+="</script>";
				return html;
			}

			public String htmlFormText(String name) {
				String html = "";
				html+="<input type='text' placeholder='"+name+"'/>";
				return html;
			}

			public String htmlFormCheckbox(String name) {
				String html = "";
				html+="<input type='checkbox' name='"+name+"' value='off' />";
				return html;
			}

			public String htmlFormRadio(String clazz, String name, String[] values) {
				String html = "";
				for(String value : values) {
					html+="<input type='radio' class='"+clazz+"' name='"+name+"' value='"+value+"'> "+value+" </input>";
				}
				return html;
			}

			public String htmlFormSubmit() {
				String html = "";
				html+="<input type='submit' name='submit' value='Submit' />";
				return html;
			}

			public String htmlFormReset() {
				String html = "";
				html+="<input type='reset' name='reset' value='Reset' />";
				return html;
			}

			public String htmlFormButton(String name, String value) {
				String html = "";
				html+="<input type='button' name='"+name+"' value='"+value+"' />";
				return html;
			}

			public String htmlValidateManualScenarioResultsJS(String project, Item scenario) {
				String html = "";
				html+="<script type=\"text/javascript\">";
				html+="function checkResults() {";
				html+="  webAppInterface.showToast(\""+project+"-"+scenario.getName()+"\");";
				Item procedures = scenario.getChild("procedures");
				for(Item procedure : procedures.getChildren()) {
					Item steps = procedure.getChild("steps");
					if(steps!=null) {
						for(Item step : steps.getChildren()) {
							String stepID = procedure.getName()+"-"+step.getProperty("STEPNUMBER");
							html+=" { ";
							html+="  var stepVerdict = document.forms['form']['"+stepID+"'].value;";
							html+="  webAppInterface.setStepVerdict(\""+stepID+"\", stepVerdict);";
							html+=" } ";
						}
					}
					Item testcases = procedure.getChild("testcases");
					if(testcases!=null) {
						for(Item testcase : testcases.getChildren()) {
							String testcaseID = testcase.getName();
							html+=" { ";
							html+="  var testcaseVerdict = document.forms['form']['"+testcaseID+"'].value;";
							html+="  webAppInterface.setTestcaseVerdict(\""+testcaseID+"\", testcaseVerdict);";
							html+=" } ";
						}
					}
				}
				html+="  webAppInterface.validateResults(\""+project+"\", \""+scenario.getName()+"\");";
				html+="}";
				html+="</script>";
				return html;
			}

			private void writeManualScenario(String name, Item scenario)
			{
				try {
					File file = new File(roothtml, name+"-scenario-"+scenario.getName()+".html");
					MyFileWriter html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("  <head>");
					html.println("    <title>%s - scenario: %s</title>", name, scenario.getName());
					html.println("  </head>");
					html.println("  <body>");

					html.println(htmlValidateManualScenarioResultsJS(name, scenario));

					html.println("<form id='form' onsubmit='return checkResults()' method='post'>");

					html.println("<H3 id='scenario-%s'>scenario: %s = %s</H3>", scenario.getName(), scenario.getName(), scenario.getProperty("TITLE"));

					Properties scenarioProperties = scenario.getProperties();

					html.println("<p><b>%s:</b> %s</p>", "TYPE", scenarioProperties.getProperty("SCENARIOTYPE"));
					html.println("<p><b>%s:</b> %s</p>", "DESCRIPTION", multiline(scenarioProperties.getProperty("DESCRIPTION"), true));

					if(scenarioProperties.containsKey("TESTAREA_ID")) {
						String testareaID = scenarioProperties.getProperty("TESTAREA_ID");
						String title = specifications.get(name).get(Specification.TESTAREAS, testareaID).getProperty("TITLE");
						html.println("<p><b>%s:</b> <a href='#testarea-%s'>%s</a> = %s</p>", "TESTAREA", testareaID, testareaID, title);
					}

					Item procedures = scenario.getChild("procedures");
					if(procedures!=null) {
						html.println("<P><B>PROCEDURES:</B></P>");
						html.println("<OL>");
						for(Item procedure : procedures.getChildren()) {
							html.println("<LI>procedure: <A HREF='#procedure-%s'>%s</A> = %s</LI>", procedure.getName(), procedure.getName(), procedure.getProperty("TITLE"));
						}
						html.println("</OL>");
					}

					Item scenarioDeployments = scenario.getChild("deployments");
					if(scenarioDeployments!=null) {
						html.println("<P><B>DEPLOYMENTS:</B></P>");
						html.println("<OL>");
						for(Item deployment : scenarioDeployments.getChildren()) {
							html.println("<LI>deployment: %s</LI>", deployment.getName());
						}
						html.println("</OL>");
					}

					Item scenarioMeasurements = scenario.getChild("measurements");
					if(scenarioMeasurements!=null) {
						html.println("<P><B>MEASUREMENTS:</B></P>");
						html.println("<OL>");
						for(Item measurement : scenarioMeasurements.getChildren()) {
							html.println("<LI>measurement: %s</LI>", measurement.getName());
						}
						html.println("</OL>");
					}

					for(Item procedure : procedures.getChildren()) {
						html.println("<H4 id='procedure-%s'>procedure: %s = %s</H4>", procedure.getName(), procedure.getName(), procedure.getProperties().getProperty("TITLE"));

						Properties procedureProperties = procedure.getProperties();

						html.println("<P><B>%s:</B> %s</P>", "TYPE", procedureProperties.getProperty("PROCEDURE_TYPE"));
						html.println("<P><B>%s:</B> %s</P>", "DESCRIPTION", multiline(procedureProperties.getProperty("DESCRIPTION"), true));

						try {
							if(procedureProperties.get("PROCEDURE_TYPE").equals("MANUAL")) {
								Item steps = procedure.getChild("steps");
								if(steps!=null) {
									html.println("<P><B>STEPS:</B></P>");
									html.println("<OL>");
									for(Item step : steps.getChildren()) {
										Properties stepProperties = step.getProperties();
										html.println("<LI>%s</LI>", multiline(stepProperties.getProperty("ACTION"), true));
										html.println("<p><b>%s:</b> %s</p>", "EXPECTED", multiline(stepProperties.getProperty("EXPECTEDRESULTS"), true));
										String comments = stepProperties.getProperty("COMMENTS");
										if(comments!=null && !comments.trim().isEmpty()) {
											html.println("<p><b>%s:</b> %s</p>", "COMMENTS", multiline(comments, false));
										}
										String stepVerdict = procedure.getName()+"-"+step.getProperty("STEPNUMBER");
										html.println("<P>%s</P>", htmlFormRadio("stepVerdict", stepVerdict, new String[] {"PASS","FAIL"}));
									}
									html.println("</OL>");
								}
							}
						} catch(Exception e) {
							error(e);
						}

						Item testcases = procedure.getChild("testcases");
						if(testcases!=null) {
							html.println("<P><B>TEST CASES:</B></P>");
							html.println("<OL>");
							for(Item testcase : testcases.getChildren()) {
								String title = specifications.get(name).get(Specification.TESTCASES, testcase.getName()).getProperty("TITLE");
								html.println("<LI>testcase:  <A HREF='#testcase-%s'>%s</A> = %s</LI>", testcase.getName(), testcase.getName(), title);
								html.println("<P>%s</P>", htmlFormRadio("testcaseVerdict", testcase.getName(), new String[] {"PASS","FAIL"}));
							}
							html.println("</OL>");
						}

					}
					html.println("<input type='submit' name='submit' value='Submit' />");
					html.println("</form>");

					html.println("  </body>");
					html.println("</html>");
					html.close();
				} catch (FileNotFoundException e) {
					error(e);
				}
			}

			private void writeRequirements(String name, MainActivity.Item tree) {
				try {
					File file = new File(roothtml, name+"-requirements.html");
					MyFileWriter html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("  <head>");
					html.println("    <title>"+name+" - requirements</title>");
					html.println("  </head>");
					html.println("  <body>");
					html.println("    <H1>"+name+" - requirements</H1>");

					Item requirements = tree.getChild("requirements");

					Item projects = tree.getChild("projects");
					for(Item project : projects.getChildren()) {
						html.println("<H1 id='project-%s'>project: %s</H1>", project.getName(), project.getName());

						html.println("<H2>REQUIREMENTS</H2>");

						Item projectRequirements = project.getChild("requirements");
						for(Item projectRequirement : projectRequirements.getChildren()) {

							Item requirement = requirements.getChild(projectRequirement.getName());
							Properties requirementProperties = requirement.getProperties();
							Properties projectRequirementProperties = projectRequirement.getProperties();

							html.println("<H3 id='requirement-%s'>requirement: %s = %s (%s)</H3>", projectRequirement.getName(), projectRequirement.getName(), 
										 requirement.getProperties().getProperty("NAME"), 
										 requirement.getProperties().getProperty("REQUIREMENT_TYPE"));

							String description = requirementProperties.getProperty("DESCRIPTION","");
							html.println("<P><B>%s:</B> %s</P>", "DESCRIPTION", description);

							String type = requirement.getProperties().getProperty("REQUIREMENT_TYPE");
							switch(type) {
								case "USER": {
										Item userRequirement = requirement.getChild(projectRequirement.getName());
										Properties userRequirementProperties = userRequirement.getProperties();

										String justification = userRequirementProperties.getProperty("JUSTIFICATION","");
										html.println("<P><B>%s:</B> %s</P>", "JUSTIFICATION", justification);

										String additionalnote = userRequirementProperties.getProperty("ADDITIONALNOTE","");
										html.println("<P><B>%s:</B> %s</P>", "ADDITIONAL NOTE", additionalnote);

										String requirementtype = userRequirementProperties.getProperty("REQUIREMENTTYPE","");
										html.println("<P><B>%s:</B> %s</P>", "REQUIREMENT TYPE", requirementtype);

										String requirementlevel = userRequirementProperties.getProperty("REQUIREMENTLEVEL","");
										html.println("<P><B>%s:</B> %s</P>", "REQUIREMENT LEVEL", requirementlevel);

										String lastchange = userRequirementProperties.getProperty("LASTCHANGEDIN","");
										html.println("<P><B>%s:</B> %s</P>", "LAST-CHANGED-IN", lastchange);

										break;
									}
								case "SOFTWARE": {
										Item softwareRequirement = requirement.getChild(projectRequirement.getName());
										Properties softwareRequirementProperties = softwareRequirement.getProperties();

										String comment = softwareRequirementProperties.getProperty("COMMENT","");
										html.println("<P><B>%s:</B> %s</P>", "COMMENT", comment);

										String structure = softwareRequirementProperties.getProperty("STRUCTURE","");
										html.println("<P><B>%s:</B> %s</P>", "STRUCTURE", structure);

										String stability = softwareRequirementProperties.getProperty("STABILITY","");
										html.println("<P><B>%s:</B> %s</P>", "STABILITY", stability);

										break;
									}
								default:
									break;
							}

							String version = requirementProperties.getProperty("VERSION","");
							html.println("<P><B>%s:</B> %s</P>", "VERSION", version);

							String priority = requirementProperties.getProperty("PRIORITY","");
							html.println("<P><B>%s:</B> %s</P>", "PRIORITY", priority);

							String verification = requirementProperties.getProperty("VERIFICATION","");
							html.println("<P><B>%s:</B> %s</P>", "VERIFICATION", verification);

							String stage = projectRequirementProperties.getProperty("VERIFICATIONSTAGE","");
							html.println("<P><B>%s:</B> %s</P>", "STAGE", stage);

							String rfw = projectRequirementProperties.getProperty("REQUESTFORWAIVER","");
							html.println("<P><B>%s:</B> %s</P>", "RFW", rfw);

							String status = projectRequirementProperties.getProperty("IMPLEMENTATIONSTATUS","");
							html.println("<P><B>%s:</B> %s</P>", "STATUS", status);

							Item testcases = projectRequirement.getChild("testcases");
							if(testcases!=null) {
								html.println("<P><B>TEST CASES:</B></P>");
								html.println("<OL>");
								for(Item testcase : testcases.getChildren()) {
									html.println("<LI>testcase: <A HREF='%s.html#testcase-%s'>%s</A></LI>", html.getName().replaceFirst("-requirements", "-details"), testcase.getName(), testcase.getName());
								}
								html.println("</OL>");
							}
						}
					}

					html.println("  </body>");
					html.println("</html>");
					html.close();
				} catch (FileNotFoundException e) {
					error(e);
				}
			}

			private void writeTree(String name, Item tree) {
				try {
					File file = new File(roothtml, name+".html");
					MyFileWriter html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("  <head>");
					html.println("    <title>"+name+"</title>");
					html.println(HTML.treeStyle());
					html.println(HTML.script());
					html.println("  </head>");
					html.println("  <body>");
					html.println("    <H1>"+name+"</H1>");
					printitem(html, tree);
					html.println("  </body>");
					html.println("</html>");
					html.close();
				} catch (FileNotFoundException e) {
					error(e);
				}
			}

			public void printitem(MyFileWriter html, Item item) {

				String type = item.getType();
				String name = item.getName();
				List<Item> children = item.getChildren();
				boolean expandable = (children!=null && children.size()>0);

				if(type!=null && name!=null) {
					String htmlfilename = null;
					String title = null;
					Properties properties = item.getProperties();
					if (properties!=null) {
						switch(type) {
							case "requirement":
								htmlfilename = html.getName()+"-requirements.html";
								title = properties.getProperty("NAME");
								break;
							case "scenarioexecution":
								htmlfilename = html.getName()+"-results.html";
								name  = properties.getProperty("START");
								title = properties.getProperty("SCENARIO");
								break;
							case "procedureexecution":
								htmlfilename = html.getName()+"-results.html";
								name  = properties.getProperty("START");
								title = properties.getProperty("PROCEDURE");
								break;
							case "verdict":
								name  = properties.getProperty("TESTCASE");
								title = properties.getProperty("VERDICT");
								break;
							default:
								htmlfilename = html.getName()+"-details.html";
								title = properties.getProperty("TITLE");
								break;
						}
					}
					if(htmlfilename!=null) {
						html.println("<li%s><span>%s: <a href='%s#%s-%s'>%s</a>%s</span>", 
									 (expandable?" class='closed'":""), 
									 type, 
									 htmlfilename, type, name, 
									 name,
									 (title==null?"":" = "+title));
					} else {
						html.println("<li%s><span>%s: %s%s</span>", 
									 (expandable?" class='closed'":""), 
									 type, 
									 name,
									 (title==null?"":" = "+title));
					}

				} else {
					if(name!=null) {
						switch(name) {
							case "steps":
								return;
							case "stepexecutions":
								return;
							case "scenarioexecutions":
								Collections.sort(children, new Comparator<Item>() { 
										@Override 
										public int compare(Item a, Item b) { 
											return a.getProperty("START").compareTo(b.getProperty("START")); 
										} 
									});
								html.println("<li%s><span>%s</span>", (expandable?" class='closed'":""), name);
								break;
							case "procedureexecutions":
								Collections.sort(children, new Comparator<Item>() { 
										@Override 
										public int compare(Item a, Item b) { 
											return a.getProperty("START").compareTo(b.getProperty("START")); 
										} 
									});
								html.println("<li%s><span>%s</span>", (expandable?" class='closed'":""), name);
								break;
							case "verdicts":
								Collections.sort(children, new Comparator<Item>() { 
										@Override 
										public int compare(Item a, Item b) { 
											return a.getProperty("TESTCASE").compareTo(b.getProperty("TESTCASE")); 
										} 
									});
								html.println("<li%s><span>%s</span>", (expandable?" class='closed'":""), name);
								break;
							default:
								html.println("<li%s><span>%s</span>", (expandable?" class='closed'":""), name);
								break;
						}
					}
				}

				if (children!=null && children.size()>0) {
					html.println("<ul class='tree'>");
					for (Item child : children) {
						printitem(html, child);
					}
					html.println("</ul>");
				}

				if(name!=null) {
					html.println("</li>");
				}
			}

			public Item populate(String name) {

				Item tree = new Item();

				Specification specification = specifications.get(name);
				if(specification==null) {
					return tree;
				}

				verbose("- baselines:");
				Item baselines = new Item(tree, null, "baselines", null);
				for (String baselineID : specification.list(Specification.BASELINE))
				{
					Properties baselineProperties = specification.get(Specification.BASELINE, baselineID);
					Item baseline = new Item(baselines, "baseline", baselineID, baselineProperties);

					Item items = new Item(baseline, null, "items", null);
					for (String itemID : specification.list(Specification.BASELINEITEM))
					{
						Properties itemProperties = specification.get(Specification.BASELINEITEM, itemID);
						if(!itemProperties.getProperty("BASELINE_PK").equals(baselineProperties.getProperty("PK"))) {
							continue;
						}

						Item item = new Item(items, "item", itemID, itemProperties);
					}
				}

				verbose("- deployments:");
				Item deployments = new Item(tree, null, "deployments", null);
				for (String deploymentID : specification.list(Specification.DEPLOYMENTS))
				{
					Properties deploymentProperties = specification.get(Specification.DEPLOYMENTS, deploymentID);
					Item deployment = new Item(deployments, "deployment", deploymentID, deploymentProperties);
				}

				verbose("- information:");
				Item information = new Item(tree, null, "information", null);
				for (String infoID : specification.list(Specification.INFORMATION))
				{
					Properties infoProperties = specification.get(Specification.INFORMATION, infoID);
					Item info = new Item(information, "info", infoID, infoProperties);
				}

				verbose("- performance:");
				Item performance = new Item(tree, null, "performance", null);
				for (String measurementID : specification.list(Specification.PERFORMANCE))
				{
					Properties measurementProperties = specification.get(Specification.PERFORMANCE, measurementID);
					Item measurement = new Item(performance, "measurement", measurementID, measurementProperties);
				}

				verbose("- requirements:");
				Item requirements = new Item(tree, null, "requirements", null);
				for (String requirementID : specification.list(Specification.REQUIREMENT)) {
					Properties requirementProperties = specification.get(Specification.REQUIREMENT, requirementID);
					Item requirement = new Item(requirements, "requirement", requirementID, requirementProperties);

					String type = requirementProperties.getProperty("REQUIREMENT_TYPE");
					switch(type) {
						case "USER": {
								Properties userRequirementProperties = specification.get(Specification.USERREQ, requirementID);
								Item userRequirement = new Item(requirement, "userrequirement", requirementID, userRequirementProperties);
								break;
							}
						case "SOFTWARE": {
								Properties softwareRequirementProperties = specification.get(Specification.SOFTREQ, requirementID);
								Item softwareRequirement = new Item(requirement, "softwarerequirement", requirementID, softwareRequirementProperties);
								break;
							}
						default:
							break;
					}
				}

				verbose("- projects:");
				Item projects = new Item(tree, null, "projects", null);
				for (String projectID : specification.list(Specification.PROJECTS))
				{
					Properties projectProperties = specification.get(Specification.PROJECTS, projectID);
					Item project = new Item(projects, "project", projectID, projectProperties);

					verbose("- - requirements:");
					Item projectrequirements = new Item(project, null, "requirements", null);
					for (String requirementID : specification.list(Specification.PROJECTREQUIREMENTS))
					{
						Properties requirementProperties = specification.get(Specification.PROJECTREQUIREMENTS, requirementID);
						if(!requirementProperties.getProperty("PROJECT_PK").equals(projectProperties.getProperty("PK"))) {
							continue;
						}

						Item requirement = new Item(projectrequirements, "requirement", requirementID, requirementProperties);

						List<String> linkedTestcases = specification.linked(Specification.REQUIREMENT+"-"+Specification.TESTCASES, requirementID);
						if(linkedTestcases == null) continue;

						Item requirementsTestcase = new Item(requirement, null, "testcases", null);
						for (String testcaseID : linkedTestcases)
						{
							Properties testcaseProperties = specification.get(Specification.TESTCASES, testcaseID);
							if(requirementProperties==null) continue;
							Item testcase = new Item(requirementsTestcase, "testcase", testcaseID, testcaseProperties);
						}
					}

					verbose("- - testareas:");
					Item testareas = new Item(project, null, "testareas", null);
					for (String testareaID : specification.list(Specification.TESTAREAS))
					{
						Properties testareaProperties = specification.get(Specification.TESTAREAS, testareaID);
						if(!testareaProperties.getProperty("PROJECT_PK").equals(projectProperties.getProperty("PK"))) continue;

						Item testarea = new Item(testareas, "testarea", testareaID, testareaProperties);

						Item features = new Item(testarea, null, "features", null);
						for (String featureID : specification.list(Specification.FEATURES))
						{
							Properties featureProperties = specification.get(Specification.FEATURES, featureID);
							if(!featureProperties.getProperty("TESTAREA_PK").equals(testareaProperties.getProperty("PK"))) continue;

							Item feature = new Item(features, "feature", featureID, featureProperties);

							Item testcases = new Item(feature, null, "testcases", null);
							for (String testcaseID : specification.list(Specification.TESTCASES))
							{
								Properties testcaseProperties = specification.get(Specification.TESTCASES, testcaseID);
								if(!testcaseProperties.getProperty("FEATURE_PK").equals(featureProperties.getProperty("PK"))) continue;

								Item testcase = new Item(testcases, "testcase", testcaseID, testcaseProperties);

								List<String> linkedRequirements = specification.linked(Specification.TESTCASES+"-"+Specification.REQUIREMENT, testcaseID);
								if(linkedRequirements == null) continue;

								Item testcaseRequirements = new Item(testcase, null, "requirements", null);
								for (String requirementID : linkedRequirements)
								{
									Properties requirementProperties = specification.get(Specification.REQUIREMENT, requirementID);
									if(requirementProperties==null) continue;
									Item requirement = new Item(testcaseRequirements, "requirement", requirementID, requirementProperties);
								}

								List<String> linkedProcedures = specification.linked(Specification.TESTCASES+"-"+Specification.PROCEDURES, testcaseID);
								if(linkedProcedures == null) continue;

								Item procedureTestcases = new Item(testcase, null, "procedures", null);
								for (String procedureID : linkedProcedures)
								{
									Properties procedureProperties = specification.get(Specification.PROCEDURES, procedureID);
									if(procedureProperties==null) continue;
									Item procedure = new Item(procedureTestcases, "procedure", procedureID, procedureProperties);
								}
							}
						}
					}

					verbose("- - scenarios:");
					Item scenarios = new Item(project, null, "scenarios", null);
					for (String scenarioID : specification.list(Specification.SCENARIOS))
					{
						Properties scenarioProperties = specification.get(Specification.SCENARIOS, scenarioID);
						if(!scenarioProperties.getProperty("PROJECT_PK").equals(projectProperties.getProperty("PK"))) {
							continue;
						}

						Item scenario = new Item(scenarios, "scenario", scenarioID, scenarioProperties);

						Item procedures = new Item(scenario, null, "procedures", null);
						for (String procedureID : specification.list(Specification.PROCEDURES))
						{
							Properties procedureProperties = specification.get(Specification.PROCEDURES, procedureID);
							if(!procedureProperties.getProperty("SCENARIO_PK").equals(scenarioProperties.getProperty("PK"))) {
								continue;
							}

							Item procedure = new Item(procedures, "procedure", procedureID, procedureProperties);

							List<String> linkedTestcases = specification.linked(Specification.PROCEDURES+"-"+Specification.TESTCASES, procedureID);
							if(linkedTestcases == null) continue;

							Item procedureTestcases = new Item(procedure, null, "testcases", null);
							for (String testcaseID : linkedTestcases)
							{
								Properties testcaseProperties = specification.get(Specification.TESTCASES, testcaseID);
								if(testcaseProperties==null) continue;
								Item testcase = new Item(procedureTestcases, "testcase", testcaseID, testcaseProperties);
							}

							try { 
								if(procedureProperties.getProperty("PROCEDURE_TYPE").equals("MANUAL")) {
									String prpk = procedureProperties.getProperty("PK");
									List<String> linkedSteps = specification.linked(Specification.PROCEDURES+"-"+Specification.STEPS, prpk);
									if(linkedSteps == null) continue;
									Item steps = new Item(procedure, null, "steps", null);
									for (String stpk : linkedSteps)
									{
										Properties stepProperties = specification.get(Specification.STEPS, stpk);
										if(!stepProperties.getProperty("MANUALPROCEDURE_PK").equals(procedureProperties.getProperty("PK"))) {
											continue;
										}
										String stepNumber = stepProperties.getProperty("STEPNUMBER");
										Item step = new Item(steps, "step", stepNumber, stepProperties);
									}
									Collections.sort(steps.getChildren(), new Comparator<Item>() {
											public int compare(Item a, Item b) {
												return a.getName().compareTo(b.getName());
											}
										});
								}
							} catch(Exception e) {
								error(e);
							}

						}

						List<String> linkedDeployments = specification.linked(Specification.SCENARIOS+"-"+Specification.DEPLOYMENTS, scenarioID);
						if(linkedDeployments!=null) {
							Item scenarioDeployments = new Item(scenario, null, "deployments", null);
							for (String deploymentID : linkedDeployments)
							{
								Properties deploymentProperties = specification.get(Specification.DEPLOYMENTS, deploymentID);
								if(deploymentProperties!=null) {
									Item scenarioDeployment = new Item(scenarioDeployments, "deployment", deploymentID, deploymentProperties);
								}
							}
						}

						List<String> linkedMeasurements = specification.linked(Specification.SCENARIOS+"-"+Specification.PERFORMANCE, scenarioID);
						if(linkedMeasurements!=null) {
							Item scenarioMeasurements = new Item(scenario, null, "measurements", null);
							for (String measurementID : linkedMeasurements)
							{
								Properties measurementProperties = specification.get(Specification.PERFORMANCE, measurementID);
								if(measurementProperties!=null) {
									Item scenarioMeasurement = new Item(scenarioMeasurements, "measurement", measurementID, measurementProperties);
								}
							}
						}

					}

					Results projectResult = testResults.get(name);
					if(projectResult!=null) {
						Item results = new Item(project, null, "results", null);
						Item scenarioExecutions = new Item(results, null, "scenarioexecutions", null);
						for (String scenarioExecutionPK : projectResult.list(Results.SCENARIOEXECUTIONS)) {
							Properties scenarioExecutionProperties = projectResult.get(Results.SCENARIOEXECUTIONS, scenarioExecutionPK);
							Item scenarioExecution = new Item(scenarioExecutions, "scenarioexecution", scenarioExecutionPK, scenarioExecutionProperties);
							List<String> linkedProcedureExecutions = projectResult.linked(Results.SCENARIOEXECUTIONS + "-" + Results.PROCEDUREEXECUTIONS, scenarioExecutionPK);
							if(linkedProcedureExecutions!=null) {
								Item procedureExecutions = new Item(scenarioExecution, null, "procedureexecutions", null);
								for (String procedureExecutionPK : linkedProcedureExecutions) {
									Properties procedureExecutionProperties = projectResult.get(Results.PROCEDUREEXECUTIONS, procedureExecutionPK);
									Item procedureExecution = new Item(procedureExecutions, "procedureexecution", procedureExecutionPK, procedureExecutionProperties);
									List<String> linkedStepExecutions = projectResult.linked(Results.PROCEDUREEXECUTIONS + "-" + Results.STEPEXECUTIONS, procedureExecutionPK);
									if(linkedStepExecutions!=null) {
										Item stepExecutions = new Item(procedureExecution, null, "stepexecutions", null);
										for (String stepExecutionPK : linkedStepExecutions) {
											Properties stepExecutionProperties = projectResult.get(Results.STEPEXECUTIONS, stepExecutionPK);
											Item stepExecution = new Item(stepExecutions, "stepexecution", stepExecutionPK, stepExecutionProperties);
										}
									}
									List<String> linkedVerdicts = projectResult.linked(Results.PROCEDUREEXECUTIONS + "-" + Results.VERDICTS, procedureExecutionPK);
									if(linkedVerdicts!=null) {
										Item verdicts = new Item(procedureExecution, null, "verdicts", null);
										for (String verdictPK : linkedVerdicts) {
											Properties verdictProperties = projectResult.get(Results.VERDICTS, verdictPK);
											Item verdict = new Item(verdicts, "verdict", verdictPK, verdictProperties);
										}
									}
								}
							}
						}
					}
				}
				return tree;
			}
		}
		
		private class LoadTask extends GenericTask 
		{
			private static final String TAG = "LoadTask";

			public LoadTask(String... args) {
				super(args);
				debug(TAG);
			}

			@Override
			protected String doInBackground(String... args) {
				debug(TAG+".doInBackground()");
				try {
					File file = writeIndex(args);
					HtmlHandler.command("load file://"+file.getAbsolutePath());
					publishProgress(100);
				} catch(Exception e) {
					error(e);
				}
				return "ok";
			}

			private File writeIndex(String[] names) throws FileNotFoundException {
				debug(TAG+".writeIndex()");
				File file = new File(roothtml, "index.html");
				MyFileWriter writer = new MyFileWriter(file);
				writer.println("<!DOCTYPE html>");
				writer.println("<html>");
				writer.println("  <head>");
				writer.println("    <title>index</title>");
				writer.println("  </head>");
				writer.println("  <body>");
				writer.println("    <ul>");
				for(final String name : names) {
					File htmlfile = new File(roothtml, name+".html");
					if(htmlfile.exists()) {
						writer.println("      <li><a href='"+name+".html'>"+name+"</a></li>");
					} else {
						writer.println("      <li>"+name+"</li>");
					}
					
				}
				writer.println("    </ul>");
				writer.println("  </body>");
				writer.println("</html>");
				writer.close();
				return file;
			}
		}
		
		/**
		 * XML component validation results XML file 
		 * expected in sharepoint
		 */
		public class XML {
			private static final String TAG = "XML";
			
			public XML() {
				debug("new "+TAG+"()");
			}

			private String xmlurl(String name) {
				String xmlurl = PreferenceManager.getDefaultSharedPreferences(context).getString("xmlurl", ".");
				return String.format("%s/%s-val-results.xml", xmlurl, name);
			}

			private String xmlpath(String name) {
				return new File(rootxml, String.format("%s-val-results.xml", name)).getAbsolutePath();
			}

			private boolean exists(String name) {
				return new File(rootxml, String.format("%s-val-results.xml", name)).exists();
			}
			
			public String download(String name) throws ConnectException {
				try {
					String from = xmlurl(name);
					String to = xmlpath(name);
					verbose(TAG+" downloading "+name+" from "+from+" to "+to);
					File file = new File(to);
					file.getParentFile().mkdirs();
					PrintWriter out = new PrintWriter(new FileOutputStream(file));
					BufferedReader reader = new BufferedReader(new InputStreamReader(((HttpURLConnection)(new URL(from).openConnection())).getInputStream(), Charset.forName("UTF-8")));
					String line;
					while ((line = reader.readLine()) != null) {
						out.println(line);
					}
					reader.close();
					out.close();
					return to;
				} catch(Exception e) {
					if(e.getMessage().startsWith("Server returned HTTP response code: 401")) {
						error(new Exception("download: UNAUTHORIZED "+name));
					} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
						error(new Exception("download: BAD REQUEST "+name));
					} else  if(e.getMessage().startsWith("Connection timed out: connect")) {
						throw new ConnectException(e.getMessage());
					} else {
						error(e);
					}
					return null;
				}
			}

			public Document getDocument(String name) {
				Document doc = null;
				try {
					String path = xmlpath(name);
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					factory.setIgnoringComments(false);
					factory.setIgnoringElementContentWhitespace(false);
					factory.setValidating(false);
					DocumentBuilder builder = factory.newDocumentBuilder();
					doc = builder.parse(new InputSource("file:"+path));
				} catch (Exception e) {
					error(e);
				}
				return doc;
			}
		}

		/**
		 * DB component validation specification SQL file
		 * expected in GIT
		 */
		public class DB {
			private static final String TAG = "DB";
			private static final String DRIVER = "org.h2.Driver";
			private static final String JDBC   = "jdbc:h2:";
			private static final String USER   = "SA";
			private static final String PSWD   = "";

			public Map<String, Connection> connections = new HashMap<>();

			public DB() throws ClassNotFoundException {
				debug("new "+TAG+"()");
				Class.forName(DRIVER);
			}

			private String giturl(String name, String branch) {
				String giturl = PreferenceManager.getDefaultSharedPreferences(context).getString("giturl", ".");
				return String.format("%s/%s.git/%s/vtest!db!%s-val-spec.sql", giturl, name, branch, name);
			}

			private String sqlpath(String name) {
				return new File(rootsql, String.format("%s-val-spec.sql", name)).getAbsolutePath();
			}
			
			private boolean sqlexists(String name) {
				return new File(rootsql, String.format("%s-val-spec.sql", name)).exists();
			}
			
			private String dbpath(String name) {
				return new File(rootdb, name).getAbsolutePath();
			}
			
			public boolean dbexists(String name) {
				return new File(rootdb, name+".mv.db").exists();
			}
			
			public boolean updated(String name) {
				File sqlfile = new File(rootsql, name+".sql");
				File dbfile = new File(rootdb, name+".mv.db");
				if(!dbfile.exists()) return false;
				return (dbfile.lastModified()>=sqlfile.lastModified());
			}
			
			public String downloadSQL(String name, String branch) throws ConnectException {
				String from = giturl(name, branch);
				String to   = sqlpath(name);
				verbose("downloading "+name+" from "+from+" to %s"+to);
				try {
					File file = new File(to);
					file.getParentFile().mkdirs();
					PrintWriter out = new PrintWriter(new FileOutputStream(file));
					BufferedReader reader = new BufferedReader(new InputStreamReader(((HttpURLConnection)(new URL(from).openConnection())).getInputStream(), Charset.forName("UTF-8")));
					String line;
					while ((line = reader.readLine()) != null) {
						out.println(line);
					}
					reader.close();
					out.close();
				} catch(Exception e) {
					if(e.getMessage().startsWith("Server returned HTTP response code: 401")) {
						error(new Exception("downloading "+name+" not authorized!"));
					} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
						error(new Exception("downloading "+name+" bad request!"));
					} else  if(e.getMessage().startsWith("Connection timed out: connect")) {
						throw new ConnectException(e.getMessage());
					} else {
						error(e);
					}
					return null;
				}
				return to;
			}

			public void importSQL(String name) {
				debug(TAG+".importSQL()");
				try {
					String sqlpath = sqlpath(name);
					String dbpath = dbpath(name);
					String from = rootsql.getAbsolutePath();
					String to = JDBC + dbpath;
					String user = USER;
					String pswd = PSWD;
					
					File sqlfile = new File(sqlpath);
					if(!sqlfile.exists()) {
						debug("missing "+name+" .sql");
						return;
					}
					
					File dbfile = new File(dbpath+".mv.db");
					if(dbfile.exists() && dbfile.lastModified()>=sqlfile.lastModified()) {
						debug("updated "+name+" .mv.db");
						return;
					}
					
					DeleteDbFiles.execute(rootdb.getAbsolutePath(), name, true);
					debug("importing "+name+" from "+from+" to "+to);
					Charset charset = null;
					boolean continueOnError = true;
					RunScript.execute(to, user, pswd, sqlpath, charset, continueOnError);
				
				} catch(SQLException e) {
					error(e);
				}
			}
			
			public void exportSQL(String name) {
				
			}
			
			public Connection connect(String name) {
				if(connections.containsKey(name))
					return connections.get(name);
				String dbpath = dbpath(name);
				Connection connection = null;
				try {
					String url = JDBC + dbpath;
					verbose("url="+url);
					String user = USER;
					String pswd = PSWD;
					connection = DriverManager.getConnection(url+";AUTO_SERVER=TRUE", user, pswd);
					connections.put(name, connection);
				} catch (SQLException e) {
					error(e);
				}
				return connection;
			}

			public void disconnect(String name) {
				if(!connections.containsKey(name)) 
					return;
				Connection connection = connections.get(name);
				connections.remove(name);
				try {
					connection.close();
				} catch (SQLException e) {
					error(e);
				}
			}

			public List<Properties> query(String name, String query) throws SQLException {
				boolean closeafter = !connections.containsKey(name);
				Connection connection = (closeafter ? connect(name) : connections.get(name));
				Statement statement = null;
				List<Properties> results = null;
				try {
					statement = connection.createStatement();
					ResultSet rs = statement.executeQuery(query);
					results = new ArrayList<>();
					ResultSetMetaData metadata = rs.getMetaData();
					while (rs.next()) {
						Properties properties = new Properties();
						for(int i=1;i<=metadata.getColumnCount();i++) {
							if(rs.getString(i)!=null) {
								properties.setProperty(metadata.getColumnLabel(i), rs.getString(i));
							}
						}
						results.add(properties);
					}
					statement.close();
				} catch (SQLException e) {
					error(e);
				} catch (Exception e) {
					error(e);
				}
				if(closeafter) {
					disconnect(name);
				}
				return results;
			}
		}
		
		class Specification {
			
			public static final String BASELINE     = "baseline";
			public static final String BASELINEITEM = "baselineitem";
			public static final String DEPLOYMENTS  = "deployments";
			public static final String INFORMATION  = "information";
			public static final String PERFORMANCE  = "performance";
			public static final String REQUIREMENT  = "requirement";
			public static final String USERREQ      = "user-requirement";
			public static final String SOFTREQ      = "software-requirement";
			public static final String PROJECTREQUIREMENTS = "project-requirements";
			public static final String PROJECTS     = "projects";
			public static final String TESTAREAS    = "testareas";
			public static final String FEATURES     = "features";
			public static final String TESTCASES    = "testcases";
			public static final String SCENARIOS    = "scenarios";
			public static final String PROCEDURES   = "procedures";
			public static final String STEPS        = "steps";

			private Map<String, Map<String, String>> pk = new HashMap<>();
			private Map<String, Map<String, Properties>> data = new HashMap<>();
			private Map<String, Map<String, Set<String>>> linked = new HashMap<>();

			private Map<String, Properties> ReleasePlan = new HashMap<>();

			private Map<String, String> mapRequirementPK = new HashMap<>();

			private Map<String, String> mapProjectPK     = new HashMap<>();
			private Map<String, String> mapTestareaPK    = new HashMap<>();
			private Map<String, String> mapFeaturePK     = new HashMap<>();
			private Map<String, String> mapTestCasePK    = new HashMap<>();
			private Map<String, Set<String>> mapTestcaseProceduresPK   = new HashMap<>();

			private Map<String, String> mapScenarioPK    = new HashMap<>();
			private Map<String, Set<String>> mapScenarioInformationPK   = new HashMap<>();
			private Map<String, Set<String>> mapScenarioMeasurementPK   = new HashMap<>();
			private Map<String, Set<String>> mapScenarioDeploymentPK    = new HashMap<>();

			private Map<String, String> mapProcedurePK   = new HashMap<>();
			private Map<String, Set<String>> mapProcedureTestcasesPK = new HashMap<>();

			private Map<String, String> mapBaselinePK  = new HashMap<>();
			private Map<String, String> mapDeploymentPK  = new HashMap<>();
			private Map<String, String> mapInformationPK = new HashMap<>();
			private Map<String, String> mapPerformancePK = new HashMap<>();

			private boolean verbose;

			public void verbose(String s) {
				if(verbose) {
					MainActivity.verbose(s);
				}
			}
			
			public Specification() {
				verbose = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("data.verbose", false);
			}
			
			public void query(String name) {
				if(db.connect(name)==null) {
					verbose("ERROR: connecting db "+name);
					return;
				}
				queryBaseline(name);
				queryDeployments(name);
				queryAdditionalInformation(name);
				queryPerformanceMeasurements(name);
				queryRequirements(name);
				queryProjects(name);
				db.disconnect(name);
			}

			public int size(String what) {
				if(!data.containsKey(what)) return 0;
				return data.get(what).size();
			}

			public List<String> list(String what) {
				if(!data.containsKey(what)) return null;
				List<String> list = new ArrayList<String>();
				list.addAll(data.get(what).keySet());
				Collections.sort(list);
				return list;
			}

			public Properties get(String what, String which) {
				if(!data.containsKey(what)) return null;
				if(!data.get(what).containsKey(which)) return null;
				return data.get(what).get(which);
			}

			public List<String> linked(String what, String which) {
				if(!linked.containsKey(what)) return null;
				if(!linked.get(what).containsKey(which)) return null;
				List<String> list = new ArrayList<String>();
				list.addAll(linked.get(what).get(which));
				Collections.sort(list);
				return list;
			}

			private Map<String, Properties> queryBaseline(String name) {
				Map<String, Properties> baseline = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM BASELINE");
					if(results!=null) {
						verbose("baselines:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String key = result.getProperty("NAME");
							if(mapBaselinePK.containsValue(key)) {
								continue;
							}
							mapBaselinePK.put(pk, key);
							baseline.put(key, result);
							verbose("baseline = "+key);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				queryBaselineItem(name);

				data.put(BASELINE, baseline);
				return baseline;
			}

			private Map<String, Properties> queryBaselineItem(String name) {
				Map<String, Properties> items = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM BASELINE_ITEM");
					if(results!=null) {
						verbose("baseline-item:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String key = result.getProperty("ID");
							items.put(key, result);
							verbose("item = "+key);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(BASELINEITEM, items);
				return items;
			}

			private Map<String, Properties> queryDeployments(String name) {
				mapDeploymentPK.clear();
				Map<String, Properties> deployments = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM DEPLOYMENT");
					if(results!=null) {
						verbose("deployments:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String key = result.getProperty("NAME");
							if(mapDeploymentPK.containsValue(key)) {
								continue;
							}
							mapDeploymentPK.put(pk, key);
							deployments.put(key, result);
							verbose("deployment = "+key);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(DEPLOYMENTS, deployments);
				return deployments;
			}

			private Map<String, Properties> queryAdditionalInformation(String name) {
				Map<String, Properties> information = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM ADDITIONAL_INFORMATION");
					if(results!=null) {
						verbose("information:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String key = result.getProperty("KEY");
							if(mapInformationPK.containsValue(key)) {
								continue;
							}
							mapInformationPK.put(pk, key);
							information.put(key, result);
							verbose("info = "+key);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(INFORMATION, information);
				return information;
			}

			private Map<String, Properties> queryPerformanceMeasurements(String name) {
				Map<String, Properties> performance = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM PERFORMANCE_MEASUREMENT ORDER BY KEY");
					if(results!=null) {
						verbose("performance:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String key = result.getProperty("KEY");
							if(mapPerformancePK.containsValue(key)) {
								continue;
							}
							mapPerformancePK.put(pk, key);
							performance.put(key, result);
							verbose("measurement = "+key);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(PERFORMANCE, performance);
				return performance;
			}

			private Map<String, Properties> queryRequirements(String name) {
				queryUserRequirements(name);
				querySoftwareRequirements(name);
				Map<String, Properties> requirements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM REQUIREMENT");
					if(results!=null) {
						verbose("requirements:");
						for(Properties result : results) {
							String id = result.getProperty("ID");
							if(requirements.containsKey(id)) {
								continue;
							}
							requirements.put(id, result);

							String requirementType = result.getProperty("REQUIREMENT_TYPE");
							switch(requirementType) {
								case "USER":
									break;
								case "SOFTWARE":
									break;
								default:
									break;
							}
							verbose("requirement = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(REQUIREMENT, requirements);
				return requirements;
			}

			private Map<String, Properties> queryUserRequirements(String name) {
				Map<String, Properties> requirements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM USER_REQUIREMENT");
					if(results!=null) {
						verbose("user-requirements:");
						for(Properties result : results) {
							String id = result.getProperty("ID");
							if(requirements.containsKey(id)) {
								continue;
							}
							requirements.put(id, result);
							verbose("user-requirement = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(USERREQ, requirements);
				return requirements;
			}

			private Map<String, Properties> querySoftwareRequirements(String name) {
				Map<String, Properties> requirements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM SOFTWARE_REQUIREMENT");
					if(results!=null) {
						verbose("software-requirements:");
						for(Properties result : results) {
							String id = result.getProperty("ID");
							if(requirements.containsKey(id)) {
								continue;
							}
							requirements.put(id, result);
							verbose("software-requirement = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(SOFTREQ, requirements);
				return requirements;
			}

			private Map<String, Properties> queryProjects(String name) {
				mapProjectPK.clear();
				Map<String, Properties> projects = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, "SELECT * FROM PROJECT");
					if(results!=null) {
						verbose("projects:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							mapProjectPK.put(pk, id);
							projects.put(id, result);
							verbose("project = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				queryProjectRequirements(name);
				queryTestAreas(name);
				queryScenarios(name);

				data.put(PROJECTS, projects);
				return projects;
			}

			private Map<String, Properties> queryProjectRequirements(String name) {
				mapRequirementPK.clear();
				Map<String, Properties> requirements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM PROJECT_REQUIREMENT"));
					if(results!=null) {
						verbose("requirements:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("REQUIREMENT_ID");
							mapRequirementPK.put(pk, id);
							requirements.put(id, result);
							verbose("requirement = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				data.put(PROJECTREQUIREMENTS, requirements);
				return requirements;
			}

			private Map<String, Properties> queryTestAreas(String name) {
				mapTestareaPK.clear();
				Map<String, Properties> testareas = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM TEST_AREA"));
					if(results!=null) {
						verbose("testareas:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							mapTestareaPK.put(pk, id);
							testareas.put(id, result);
							verbose("testarea = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				queryFeatures(name);
				data.put(TESTAREAS, testareas);
				return testareas;
			}

			private Map<String, Properties> queryFeatures(String name) {
				mapFeaturePK.clear();
				Map<String, Properties> features = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM FEATURE"));
					if(results!=null) {
						verbose("features:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							// I am forced to fix the feature ID
							id = mapTestareaPK.get(result.getProperty("TESTAREA_PK"))+"-"+id;
							result.setProperty("ID", id);
							mapFeaturePK.put(pk, id);
							features.put(id, result);
							verbose("feature = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				queryTestCases(name);
				data.put(FEATURES, features);
				return features;
			}

			private Map<String, Properties> queryTestCases(String name) {
				mapTestCasePK.clear();
				Map<String, Properties> testcases = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM TEST_CASE"));
					if(results!=null) {
						verbose("testcases:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							mapTestCasePK.put(pk, id);
							testcases.put(id, result);
							verbose("testcase = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				queryTestcaseRequirements(name);
				data.put(TESTCASES, testcases);
				return testcases;
			}

			private Map<String, Properties> queryTestcaseRequirements(String name) {
				Map<String, Properties> requirements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM TEST_CASE_PROJECT_REQUIREMENT"));
					if(results!=null) {
						for(Properties result : results) {
							String tcpk = result.getProperty("TEST_CASE_PK");
							String prpk = result.getProperty("PROJECTREQUIREMENTS_PK");

							String tcid = mapTestCasePK.get(tcpk);
							String prid = mapRequirementPK.get(prpk);

							if(!linked.containsKey(TESTCASES+"-"+REQUIREMENT)) {
								linked.put(TESTCASES+"-"+REQUIREMENT, new HashMap<String, Set<String>>());
							}
							if(!linked.get(TESTCASES+"-"+REQUIREMENT).containsKey(tcid)) {
								linked.get(TESTCASES+"-"+REQUIREMENT).put(tcid, new HashSet<String>());
							}
							linked.get(TESTCASES+"-"+REQUIREMENT).get(tcid).add(prid);

							if(!linked.containsKey(REQUIREMENT+"-"+TESTCASES)) {
								linked.put(REQUIREMENT+"-"+TESTCASES, new HashMap<String, Set<String>>());
							}
							if(!linked.get(REQUIREMENT+"-"+TESTCASES).containsKey(prid)) {
								linked.get(REQUIREMENT+"-"+TESTCASES).put(prid, new HashSet<String>());
							}
							linked.get(REQUIREMENT+"-"+TESTCASES).get(prid).add(tcid);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				return requirements;
			}

			private Map<String, Properties> queryScenarios(String name) {
				mapScenarioPK.clear();
				Map<String, Properties> scenarios = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM SCENARIO"));
					if(results!=null) {
						verbose("scenarios:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							mapScenarioPK.put(pk, id);
							scenarios.put(id, result);
							verbose("scenario = "+id);

							// PATCH
							String tapk = result.getProperty("TESTAREA_PK");
							String testareaID = mapTestareaPK.get(tapk);
							if(testareaID!=null) {
								result.put("TESTAREA_ID", testareaID);
							}
						}
					}
				} catch (Exception e) {
					error(e);
				}

				queryProcedures(name);

				queryScenarioInformations(name);
				queryScenarioMeasurements(name);
				queryScenarioDeployments(name);

				data.put(SCENARIOS, scenarios);
				return scenarios;
			}

			private Map<String, Properties> queryScenarioInformations(String name) {
				mapScenarioInformationPK.clear();
				Map<String, Properties> info = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM SCENARIO_ADDITIONAL_INFORMATION"));
					if(results!=null) {
						for(Properties result : results) {
							String spk = result.getProperty("SCENARIO_PK");
							String pk = result.getProperty("ADDITIONALINFORMATION_PK");
							if(!mapInformationPK.containsKey(pk)) {
								error(new Exception(String.format("additional information pk %s not found!",pk)));
								continue;
							}
							String key = mapInformationPK.get(pk);
							info.put(key, result);

							if(!mapScenarioInformationPK.containsKey(spk)) {
								mapScenarioInformationPK.put(spk, new HashSet<String>());
							}
							mapScenarioInformationPK.get(spk).add(pk);

							String infoID = mapInformationPK.get(pk);
							String scenarioID = mapScenarioPK.get(spk);
							String linkedName = SCENARIOS + "-" + INFORMATION;
							if (!linked.containsKey(linkedName))
							{
								linked.put(linkedName, new HashMap<String, Set<String>>());
							}
							if(!linked.get(linkedName).containsKey(scenarioID)) {
								linked.get(linkedName).put(scenarioID, new HashSet<String>());
							}
							linked.get(linkedName).get(scenarioID).add(infoID);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				return info;
			}

			private Map<String, Properties> queryScenarioMeasurements(String name) {
				mapScenarioMeasurementPK.clear();
				Map<String, Properties> measurements = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM SCENARIO_PERFORMANCE_MEASUREMENT"));
					if(results!=null) {
						for(Properties result : results) {
							String spk = result.getProperty("SCENARIO_PK");
							String pk = result.getProperty("PERFORMANCEMEASUREMENTS_PK");
							if(!mapPerformancePK.containsKey(pk)) {
								error(new Exception(String.format("scenario measurement pk %s not found!",pk)));
								continue;
							}
							String key = mapPerformancePK.get(pk);
							measurements.put(key, result);

							if(!mapScenarioMeasurementPK.containsKey(spk)) {
								mapScenarioMeasurementPK.put(spk, new HashSet<String>());
							}
							mapScenarioMeasurementPK.get(spk).add(pk);

							String measurementID = this.mapPerformancePK.get(pk);
							String scenarioID = mapScenarioPK.get(spk);
							String linkedName = SCENARIOS + "-" + PERFORMANCE;
							if (!linked.containsKey(linkedName))
							{
								linked.put(linkedName, new HashMap<String, Set<String>>());
							}
							if(!linked.get(linkedName).containsKey(scenarioID)) {
								linked.get(linkedName).put(scenarioID, new HashSet<String>());
							}
							linked.get(linkedName).get(scenarioID).add(measurementID);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				return measurements;
			}

			private Map<String, Properties> queryScenarioDeployments(String name) {
				mapScenarioDeploymentPK.clear();
				Map<String, Properties> deployments = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM SCENARIO_DEPLOYMENT"));
					if(results!=null) {
						for(Properties result : results) {
							String spk = result.getProperty("SCENARIO_PK");
							String pk = result.getProperty("DEPLOYMENTS_PK");
							if(!mapDeploymentPK.containsKey(pk)) {
								error(new Exception(String.format("scenario deployment pk %s not found!",pk)));
								continue;
							}
							String key = mapDeploymentPK.get(pk);
							deployments.put(key, result);

							if(!mapScenarioDeploymentPK.containsKey(spk)) {
								mapScenarioDeploymentPK.put(spk, new HashSet<String>());
							}
							mapScenarioDeploymentPK.get(spk).add(pk);

							String deploymentID = this.mapDeploymentPK.get(pk);
							String scenarioID = mapScenarioPK.get(spk);
							String linkedName = SCENARIOS + "-" + DEPLOYMENTS;
							if (!linked.containsKey(linkedName))
							{
								linked.put(linkedName, new HashMap<String, Set<String>>());
							}
							if(!linked.get(linkedName).containsKey(scenarioID)) {
								linked.get(linkedName).put(scenarioID, new HashSet<String>());
							}
							linked.get(linkedName).get(scenarioID).add(deploymentID);

						}
					}
				} catch (Exception e) {
					error(e);
				}
				return deployments;
			}

			private Map<String, Properties> queryProcedures(String name) {
				mapProcedurePK.clear();
				Map<String, Properties> procedures = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM PROCEDURE"));
					if(results!=null) {
						verbose("procedures:");
						for(Properties result : results) {
							String pk = result.getProperty("PK");
							String id = result.getProperty("ID");
							// I am forced to fix the procedure ID
							id = mapScenarioPK.get(result.getProperty("SCENARIO_PK"))+"-"+id;
							result.setProperty("ID", id);
							mapProcedurePK.put(pk, id);
							procedures.put(id, result);
							verbose("procedure = "+id);
						}
					}
				} catch (Exception e) {
					error(e);
				}

				queryManualProcedureSteps(name);
				queryProcedureTestcases(name);

				data.put(PROCEDURES, procedures);
				return procedures;
			}

			private Map<String, Properties> queryManualProcedureSteps(String name) {
				Map<String, Properties> steps = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM MANUAL_PROCEDURE_STEP"));
					if(results!=null) {
						for(Properties result : results) {
							String stpk = result.getProperty("PK");
							String mppk = result.getProperty("MANUALPROCEDURE_PK");
							steps.put(stpk, result);

							String linkedName = PROCEDURES+"-"+STEPS;
							if(!linked.containsKey(linkedName)) {
								linked.put(linkedName, new HashMap<String, Set<String>>());
							}
							if(!linked.get(linkedName).containsKey(mppk)) {
								linked.get(linkedName).put(mppk, new HashSet<String>());
							}
							linked.get(linkedName).get(mppk).add(stpk);

						}
					}
				} catch (Exception e) {
					error(e);
				}

				data.put(STEPS, steps);
				return steps;
			}

			private Map<String, Properties> queryProcedureTestcases(String name) {
				Map<String, Properties> testcases = new HashMap<>(); 
				List<Properties> results;
				try {
					results = db.query(name, String.format("SELECT * FROM PROCEDURE_TEST_CASE"));
					if(results!=null) {
						for(Properties result : results) {
							String tcpk = result.getProperty("TESTCASES_PK");
							String prpk = result.getProperty("PROCEDURE_PK");

							String tcid = mapTestCasePK.get(tcpk);
							String prid = mapProcedurePK.get(prpk);

							if(!linked.containsKey(TESTCASES+"-"+PROCEDURES)) {
								linked.put(TESTCASES+"-"+PROCEDURES, new HashMap<String, Set<String>>());
							}
							if(!linked.get(TESTCASES+"-"+PROCEDURES).containsKey(tcid)) {
								linked.get(TESTCASES+"-"+PROCEDURES).put(tcid, new HashSet<String>());
							}
							linked.get(TESTCASES+"-"+PROCEDURES).get(tcid).add(prid);

							if(!linked.containsKey(PROCEDURES+"-"+TESTCASES)) {
								linked.put(PROCEDURES+"-"+TESTCASES, new HashMap<String, Set<String>>());
							}
							if(!linked.get(PROCEDURES+"-"+TESTCASES).containsKey(prid)) {
								linked.get(PROCEDURES+"-"+TESTCASES).put(prid, new HashSet<String>());
							}
							linked.get(PROCEDURES+"-"+TESTCASES).get(prid).add(tcid);
						}
					}
				} catch (Exception e) {
					error(e);
				}
				return testcases;
			}

			public void readCSV(String path) {
				try {
					BufferedReader reader = new BufferedReader(new FileReader(path));
					String line;
					line = reader.readLine();
					while ((line = reader.readLine()) != null) {
						String[] split = line.split(";");
						Properties properties = new Properties();
						String id = split[0];
						properties.setProperty("ID", id);
						String swir = split[1];
						swir = swir.replaceAll("\\[ ", "");
						swir = swir.replaceAll(" \\]", "");
						swir = swir.replaceAll(" ", ",");
						properties.setProperty("SW/IR", swir);
						String version = "Not implemented";
						for(int i=2; i<split.length; i++) {
							if(split[i].equals("YES")) {
								switch(i) {
									case 2: // IR1
										version = "0.1.0";
										break;
									case 3: // IR2
										version = "0.2.0";
										break;
									case 4: // IR2D1
										version = "0.2.1";
										break;
									case 5: // IR2D2
										version = "0.2.2";
										break;
									case 6: // IR3
										version = "0.3.0";
										break;
									case 7: // IR4
										version = "0.4.0";
										break;
									case 8: // IR5
										version = "0.5.0";
										break;
									default:
										version = "Not implemented";
										break;
								}
								break;
							}
						}
						properties.setProperty("VERSION", version);
						ReleasePlan.put(id, properties);
					}
				} catch(Exception e) {
					error(e);
				}
			}
		}
		
		class Results {

			public static final String SCENARIOEXECUTIONS = "scenarioexecutions";
			public static final String PROCEDUREEXECUTIONS = "procedureexecutions";
			public static final String STEPEXECUTIONS = "stepexecutions";
			public static final String VERDICTS = "verdicts";

			private Map<String, Map<String, Properties>> data = new HashMap<>();
			private Map<String, Map<String, Set<String>>> linked = new HashMap<>();

			private Document doc;

			public int size(String what) {
				if(!data.containsKey(what)) return 0;
				return data.get(what).size();
			}

			public List<String> list(String what) {
				if(!data.containsKey(what)) return null;
				List<String> list = new ArrayList<String>();
				list.addAll(data.get(what).keySet());
				//Collections.sort(list);
				return list;
			}

			public Properties get(String what, String which) {
				if(!data.containsKey(what)) return null;
				if(!data.get(what).containsKey(which)) return null;
				return data.get(what).get(which);
			}

			private void link(String what, String which, String with) {
				if(!linked.containsKey(what)) {
					linked.put(what, new HashMap<String, Set<String>>());
				}
				if(!linked.get(what).containsKey(which)) {
					linked.get(what).put(which, new HashSet<String>());
				}
				linked.get(what).get(which).add(with);
			}

			public List<String> linked(String what, String which) {
				if(!linked.containsKey(what)) return null;
				if(!linked.get(what).containsKey(which)) return null;
				List<String> list = new ArrayList<String>();
				list.addAll(linked.get(what).get(which));
				//Collections.sort(list);
				return list;
			}

			public void readXML(String name) {
				try {
					for(String what : new String[] {SCENARIOEXECUTIONS, PROCEDUREEXECUTIONS, STEPEXECUTIONS, VERDICTS}) {
						data.put(what, new HashMap<String, Properties>());
					}

					doc = xml.getDocument(name);
					if(doc!=null) {
						Element root = doc.getDocumentElement();
						NodeList nodes = root.getChildNodes();
						for(int i=0; i< nodes.getLength(); i++) { 
							Node node = nodes.item(i);
							switch(node.getNodeName()) {
								case "projects":
								case "requirements":
								case "deployments":
								case "baselines":
									break;
								case "scenarioExecutions":
									getScenarioExecutions(node);
									break;
								case "options":
									break;
								default:
									break;
							}
						}
					}
				} catch(Exception e) {
					error(e);
				}
			}

			private void getScenarioExecutions(Node node) {
				Map<String, Properties> scenarios = data.get(SCENARIOEXECUTIONS);
				NodeList children = node.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					switch(child.getNodeName()) {
						case "scenarioExecution": {
								Properties properties = getScenarioExecution(child);
								String pk = properties.getProperty("PK");
								scenarios.put(pk, properties);
								break;
							}
						default:
							break;
					}
				}
			}

			private Properties getScenarioExecution(Node node) {
				Properties properties = new Properties();
				if(node instanceof Element) {
					properties.setProperty("PK", ((Element)node).getAttribute("pk"));
					NodeList children = node.getChildNodes();
					for(int i=0; i< children.getLength(); i++) { 
						Node child = children.item(i);
						switch(child.getNodeName()) {
							case "projectId":
								properties.setProperty("PROJECT", child.getTextContent());
								break;
							case "scenarioId":
								properties.setProperty("SCENARIO", child.getTextContent());
								break;
							case "version":
								properties.setProperty("VERSION", child.getTextContent());
								break;
							case "startTime":
								properties.setProperty("START", child.getTextContent());
								break;
							case "endTime":
								properties.setProperty("END", child.getTextContent());
								break;
							case "testLogPath":
								properties.setProperty("LOGPATH", child.getTextContent());
								break;
							case "testLogRevision":
								properties.setProperty("LOGREVISION", child.getTextContent());
								break;
							case "additionalInformationExecutions":
								properties.setProperty("INFO", child.getTextContent());
								break;
							case "procedureExecutions": 
								getProcedureExecutions(child, properties.getProperty("SCENARIO"));
								break;
							case "performanceMeasurementExecutions":
								break;
							default:
								break;
						}
					}
				}
				return properties;
			}

			private void getProcedureExecutions(Node node, String scenario) {
				Map<String, Properties> procedures = data.get(PROCEDUREEXECUTIONS);
				NodeList children = node.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					Properties properties = null;
					switch(child.getNodeName()) {
						case "automatedProcedureExecution":
							properties = getAutomatedProcedureExecution(child);
							break;
						case "manualProcedureExecution": 
							properties = getManualProcedureExecution(child);
							break;
						default: 
							break;
					}
					if(properties!=null) {
						String pk = properties.getProperty("PK");

						// PATCH
						String id = properties.getProperty("PROCEDURE");
						properties.setProperty("PROCEDURE", scenario+"-"+id);

						procedures.put(pk, properties);
						link(SCENARIOEXECUTIONS+"-"+PROCEDUREEXECUTIONS, properties.getProperty("SCENARIOEXECUTION_PK"), pk);
					}
				}
			}

			private Properties getManualProcedureExecution(Node node) {
				Properties properties = new Properties();
				if(node instanceof Element) {
					properties.setProperty("PK", ((Element)node).getAttribute("pk"));
					NodeList children = node.getChildNodes();
					for(int i=0; i< children.getLength(); i++) { 
						Node child = children.item(i);
						switch(child.getNodeName()) {
							case "projectId":
								properties.setProperty("PROJECT", child.getTextContent());
								break;
							case "scenarioExecution":
								properties.setProperty("SCENARIOEXECUTION_PK", child.getTextContent());
								break;
							case "procedureId":
								properties.setProperty("PROCEDURE", child.getTextContent());
								break;
							case "startTime":
								properties.setProperty("START", child.getTextContent());
								break;
							case "endTime":
								properties.setProperty("END", child.getTextContent());
								break;
							case "procedureVerdict":
								properties.setProperty("SUMMARY", child.getTextContent());
								break;
							case "comment":
								properties.setProperty("COMMENT", child.getTextContent());
								break;
							case "testCaseVerdicts":
								getVerdict(child);
								break;
							case "stepExecutions":
								getSteps(child);
								break;
							default:
								break;
						}
					}
				}
				return properties;
			}

			private Properties getAutomatedProcedureExecution(Node node) {
				Properties properties = new Properties();
				if(node instanceof Element) {
					properties.setProperty("PK", ((Element)node).getAttribute("pk"));
					NodeList children = node.getChildNodes();
					for(int i=0; i< children.getLength(); i++) { 
						Node child = children.item(i);
						switch(child.getNodeName()) {
							case "projectId":
								properties.setProperty("PROJECT", child.getTextContent());
								break;
							case "scenarioExecution":
								properties.setProperty("SCENARIOEXECUTION_PK", child.getTextContent());
								break;
							case "procedureId":
								properties.setProperty("PROCEDURE", child.getTextContent());
								break;
							case "startTime":
								properties.setProperty("START", child.getTextContent());
								break;
							case "endTime":
								properties.setProperty("END", child.getTextContent());
								break;
							case "procedureVerdict":
								properties.setProperty("SUMMARY", child.getTextContent());
								break;
							case "testCaseVerdicts":
								getVerdict(child);
								break;
							default:
								break;
						}
					}
				}
				return properties;
			}

			private Properties getVerdict(Node node) {
				Map<String, Properties> verdicts = data.get(VERDICTS);
				Properties properties = new Properties();
				if(node instanceof Element) {
					properties.setProperty("PK", ((Element)node).getAttribute("pk"));
					NodeList children = node.getChildNodes();
					for(int i=0; i< children.getLength(); i++) { 
						Node child = children.item(i);
						switch(child.getNodeName()) {
							case "projectId":
								properties.setProperty("PROJECT", child.getTextContent());
								break;
							case "procedureExecution":
								properties.setProperty("PROCEDUREEXECUTION_PK", child.getTextContent());
								break;
							case "testCaseId": {
									String testcase = child.getTextContent();
									testcase = testcase.replaceAll("_", "-");
									if(!testcase.equals(testcase.trim())) {
										error(new Exception("spaces before/after the testcase ("+testcase+")"));
										testcase = testcase.trim();
									}
									properties.setProperty("TESTCASE", testcase);
									break;
								}
							case "verdict":
								properties.setProperty("VERDICT", child.getTextContent());
								break;
							default:
								break;
						}
					}
					verdicts.put(properties.getProperty("PK"), properties);
					link(PROCEDUREEXECUTIONS+"-"+VERDICTS, properties.getProperty("PROCEDUREEXECUTION_PK"), properties.getProperty("PK"));
				}
				return properties;
			}

			private void getSteps(Node node) {
				Map<String, Properties> steps = data.get(STEPEXECUTIONS);
				NodeList children = node.getChildNodes();
				for(int i=0; i< children.getLength(); i++) { 
					Node child = children.item(i);
					switch(child.getNodeName()) {
						case "stepExecution": {
								Properties properties = getStep(child);
								String pk = properties.getProperty("PK");
								steps.put(pk, properties);
								link(PROCEDUREEXECUTIONS+"-"+STEPEXECUTIONS, properties.getProperty("PROCEDUREEXECUTION_PK"), pk);
								break;
							}
						default:
							break;
					}
				}
			}

			private Properties getStep(Node node) {
				Properties properties = new Properties();
				if(node instanceof Element) {
					properties.setProperty("PK", ((Element)node).getAttribute("pk"));
					NodeList children = node.getChildNodes();
					for(int i=0; i< children.getLength(); i++) { 
						Node child = children.item(i);
						switch(child.getNodeName()) {
							case "stepNumber":
								properties.setProperty("STEP", child.getTextContent());
								break;
							case "manualProcedureExecution":
								properties.setProperty("PROCEDUREEXECUTION_PK", child.getTextContent());
								break;
							case "stepVerdict":
								properties.setProperty("EXECUTED", child.getTextContent());
								break;
							default:
								break;
						}
					}
				}
				return properties;
			}
		}
	}
	
	private SharedPreferences preferences;
	
	void importPreferences(String path) {
		try {
			File file = new File(tms.getRoot(), path);
			Properties properties = new Properties();
			properties.loadFromXML(new FileInputStream(file));
			SharedPreferences.Editor editor = preferences.edit();
			for (Object key : properties.keySet()) {
				if(key instanceof String) {
					Object value = properties.get(key);
					if(value instanceof String) {
						editor.putString((String)key, (String)value);
					}
				}
			}
			editor.apply();
			editor.commit();
		} catch (Exception e) {
			error(e);
		}
	}
	
	private void updatePreference(SharedPreferences preferences, String key) {
		try {
			switch(key) {
				case "verbose":
					verbose = preferences.getBoolean("verbose", false);
					System.out.println(key+" = "+(verbose?"YES":"NO"));
					break;
				case "root":
					System.out.println(key+" = "+tms.getRoot().getAbsolutePath());		
					break;
				case "giturl":
					System.out.println(key+" = "+preferences.getString(key, ""));
					break;
				case "xmlurl":
					System.out.println(key+" = "+preferences.getString(key, ""));
					break;
				case "repos":
					System.out.println(key+",,,");
					for(String repo : preferences.getString(key, "").trim().split("\n")) {
						System.out.println("  "+repo);
					}
					break;
				default:
					System.out.println("unexpected "+key);
					break;
			}
		} catch (Exception e) {
			error(e);
		}
	}
	
	public static class MyPreferencesActivity extends PreferenceActivity {
		
		@Override
		protected void onCreate(Bundle savedInstanceState) { 
			super.onCreate(savedInstanceState); 
			getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit(); 
		} 
		
		public static class MyPreferenceFragment extends PreferenceFragment { 
			@Override 
			public void onCreate(final Bundle savedInstanceState) { 
				super.onCreate(savedInstanceState); 
				addPreferencesFromResource(R.xml.preferences); 
			} 
		} 
	}
	
	public static class MyFileWriter extends PrintWriter {

		private String name;

		public String getName() {
			return name;
		}

		private File file;

		public File getFile() {
			return file;
		}

		public MyFileWriter(File file) throws FileNotFoundException {
			super(new FileOutputStream(file));
			this.file = file;
			this.name = file.getName().substring(0, file.getName().indexOf("."));
		}

		public void println(String fmt, Object... args) {
			println(String.format(fmt, args));
		}
		
	}
	
	public static class Item implements Serializable {

		private String unique(String name) {
			if(index.containsKey(name)) {
				int i = 1;
				while(index.containsKey(name+"-"+i)) {
					i++;
				}
				name+="-"+i;
			}
			return name;
		}
		
		private String name;
		private String type;
		private Item parent;
		private Properties properties = new Properties();
		private List<Item> children = new ArrayList<>();
		private Map<String, Item> index = new HashMap<>();
		
		public Item() {
		}

		public Item(Item parent, String type, String name, Properties properties) {
			this.type = type;
			this.name = name;
			this.properties = properties;
			setParent(parent);
		}

		public boolean hasChildren() {
			return children.size()>0;
		}

		public List<Item> getChildren() {
			return children;
		}
		
		public void  setChildren(List<Item> children) {
			this.children = children;
		}
		
		public Item getChild(String name) {
			for(Item child : children) {
				if(child.getName().equals(name)) {
					return child;
				}
			}
			return null;
		}
		
		public Item getParent() {
			return parent;
		}
		
		private void setParent(Item parent) {
			if(this.parent!=null) {
				this.parent.children.remove(this);
			}
			this.parent = parent;
			if(parent!=null) {
				parent.children.add(this);
				parent.index.put(parent.unique(this.name), this);
			}
		}
		
		public Item getRoot() {
			parent = getParent();
			while(parent.getParent()!=null) {
				parent = parent.getParent();
			}
			return parent;
		}

		public String getType() {
			return type;
		}

		public String getName() {
			return name;
		}

		public Properties getProperties() {
			return properties;
		}
		
		public void setProperties(Properties properties) {
			this.properties = properties;
		}
		
		public String getProperty(String key) {
			return properties.getProperty(key);
		}
		
		public void setProperty(String key, String value) {
			properties.setProperty(key, value);
		}
		
	}
	public static class HTML {

		public static String treeStyle() {
			String html = "";
			html+="<style>";
			html+=".tree { position:relative; list-style:none; margin-left:0; padding-left:1.2em; }";
			html+=".closed:before { content:\"+\"; position:absolute; left:0; }";
			html+=".open:before   { content:\"-\"; position:absolute; left:0; }";
			html+=".none:before   { content:\"\";  position:absolute; left:0; }";
			html+="</style>";
			return html;
		}

		public static String script() {
			String html = "";
			html+="<script src=\"http://code.jquery.com/jquery-1.10.1.min.js\"></script>";
			html+="<script type=\"text/javascript\">";
			html+="$(function(){";
			html+=jsToggleAll();
			html+=jsToggle();
			html+="});";
			html+="</script>";
			return html;
		}

		public static String jsToggleAll() {
			String html = "";
			html+="  $('.tree').find('UL').toggle(0);";
			return html;
		}

		public static String jsToggle() {
			String html = "";
			html+="  $('.tree').find('SPAN').click(function(e){";
			html+="	   $(this).parent().children().toggle();";
			html+="	   $(this).toggle();";
			html+="	   if($(this).parent().is('.closed')) {";
			html+="	       $(this).parent().removeClass('closed');";
			html+="	       $(this).parent().addClass('open');";
			html+="	   } else {";
			html+="	     if($(this).parent().is('.open')) {";
			html+="	       $(this).parent().removeClass('open');";
			html+="	       $(this).parent().addClass('closed');";
			html+="      }";
			html+="    }";
			html+="	 });";
			return html;
		}

	}
	
	public class Warning extends Exception {
		public Warning(String fmt, Object... args) {
			super(String.format(fmt,args));
		}
	}

	public class Error extends Exception {
		public Error(String fmt, Object... args) {
			super(String.format(fmt,args));
		}
	}
	
	
	public void println(String fmt, Object... args) {
		System.out.printf(fmt+"<br>\n", args);
	}

	public void println() {
		println("");
	}

	
	class Settings {
		
		private static final String TAG = "Settings";
		
		final Properties properties = new Properties();
		final File file = new File(tms.getRoot(), "settings.xml");

		void load() {
			try {
				if(verbose) println(TAG+".load()..."+file.getAbsolutePath());
				properties.loadFromXML(new FileInputStream(file));
			} catch (Exception e) {
				error(e);
			}
		}

		void save() {
			try {
				if(verbose) println(TAG+".save()..."+file.getAbsolutePath());
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				properties.storeToXML(new FileOutputStream(file), sdf.format(System.currentTimeMillis()));
			} catch (Exception e) {
				error(e);
			}
		}

		String getProperty(String key, String defaultValue)  {
			return properties.getProperty(key, defaultValue);
		}

		void setProperty(String key, String value)  {
			properties.setProperty(key, value);
		}

	}
	
	public void reposDialog() {
		
		LayoutInflater layout = LayoutInflater.from(this);
        View view = layout.inflate(R.layout.repos, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        final EditText reposText = view.findViewById(R.id.REPOS);
		reposText.setText(preferences.getString("repos", "")); 
        builder.setTitle("REPOS");
        builder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					try {
						final String repos = reposText.getText().toString();
						if(repos==null) { 
							Toast.makeText(MainActivity.this,"Invalid repos", Toast.LENGTH_LONG).show();
							reposDialog();
							return;
						}
						preferences.edit().putString("repos", repos);
						preferences.edit().commit();
					} catch(Exception e) {
						Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
			});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {                       
					dialog.cancel();
				}
			});
		builder.show();   
	}
	
	static class AuthenticationDialog
	{

		private static AuthenticationDialog instance;
		
		public static AuthenticationDialog instance(Context context) {
			if(instance==null) {
				instance = new AuthenticationDialog(context);
			}
			return instance;
		}
		
		private Context context;

		private AlertDialog.Builder builder;
		private EditText usertext;
		private EditText pswdtext;
		
		public AuthenticationDialog(Context context) {
			this.context = context;
			init();
		}
		
		public void init() {
			
		}
		
		void show() {
			LayoutInflater layout = LayoutInflater.from(context);
			View view = layout.inflate(R.layout.login, null);

			usertext = view.findViewById(R.id.USERNAME);
			usertext.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("user", ""));

			pswdtext = view.findViewById(R.id.PASSWORD);
			pswdtext.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("pswd", ""));

			builder = new AlertDialog.Builder(context);
			builder.setView(view);
			builder.setTitle("LOGIN");
			builder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						try {
							final String pswd = pswdtext.getText().toString();
							final String user = usertext.getText().toString();
							if(user==null || pswd==null) { 
								Toast.makeText(context,"Invalid username or password", Toast.LENGTH_LONG).show();
								show();
								return;
							}
							PreferenceManager.getDefaultSharedPreferences(context).edit().putString("user", user).apply();
							PreferenceManager.getDefaultSharedPreferences(context).edit().putString("pswd", pswd).apply();
							authenticate(user, pswd);
						} catch(Exception e) {
							Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
						}
					}
				});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {                       
						dialog.cancel();
					}
				});
			builder.show();                                     
		}
		
		public void authenticate(final String user, final String pswd) {
			Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(user, pswd.toCharArray());
					}
				});
				
			MenuItem item = ((MainActivity)context).getMenu().findItem(R.id.LOGIN);
			item.setIcon(context.getResources().getDrawable(R.drawable.user2));
		}
		
	}
	
	

	
}
