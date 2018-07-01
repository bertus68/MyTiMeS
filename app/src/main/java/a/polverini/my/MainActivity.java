package a.polverini.my;

import android.app.*;
import android.os.*;
import android.webkit.*;
import java.io.*;
import android.widget.*;
import android.content.*;
import java.util.*;
import org.h2.tools.*;
import java.net.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import java.nio.charset.*;
import java.sql.*;
import a.polverini.my.MainActivity.*;
import android.view.*;
import a.polverini.my.MainActivity.Stack.*;
import java.text.*;
import android.view.View.*;
import android.view.autofill.*;
import android.content.SharedPreferences.*;
import android.preference.*;

public class MainActivity extends Activity 
{
	private boolean verbose = true;
	private HtmlHandler handler;
	private ProgressBar progress; 
	private WebView webView;
	private Menu menu;
	
	private SharedPreferences preferences;
	private File rootdir;
	private File sqldir;
	private File xmldir;
	private File htmldir;
	
	private Stack stack;
	private DB db;
	private XML xml;
	private Map<String, Specification> specifications = new HashMap <>();
	private Map<String, Results> testResults = new HashMap <>();
	
	void importPreferences(String path) {
		try {
			File file = new File(rootdir, path);
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
			print(e);
		}
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
		progress = this.findViewById(R.id.PROGRESS);

		webView = this.findViewById(R.id.WEBVIEW);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setBuiltInZoomControls(true);
		webView.setWebViewClient(new MyWebViewClient());
		webView.addJavascriptInterface(new MyWebAppInterface(this), "webAppInterface"); 

		handler = new HtmlHandler(webView);
		print("<h1>MyTiMeS v0.8.0</h1>");
		print("A.Polverini (2018)<p/>");
		
		preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext()); 
		preferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
					updatePreference(preferences, key);
				}
			});
		
		updatePreference(preferences, "verbose");
		updatePreference(preferences, "rootdir");
		updatePreference(preferences, "giturl");
		updatePreference(preferences, "xmlurl");
		updatePreference(preferences, "repos");
		
		try {
			db = new DB();
		} catch (Exception e) {
			print(e);
		}

		try {
			xml = new XML();
		} catch (Exception e) {
			print(e);
		}

		stack = new Stack();
    }
	
	private void updatePreference(SharedPreferences preferences, String key) {
		try {
			switch(key) {
				case "verbose":
					verbose = preferences.getBoolean("verbose", false);
					println("%s = %s",key,verbose?"si":"no");
					break;
				case "rootdir":
					rootdir = new File(Environment.getExternalStorageDirectory(), preferences.getString("rootdir", "tmp"));
					println("%s = %s",key,rootdir.getAbsolutePath());
					
					sqldir = new File(rootdir, "sql");
					if(!sqldir.exists()) {
						sqldir.mkdirs();
					}

					xmldir = new File(rootdir, "xml");
					if(!xmldir.exists()) {
						xmldir.mkdirs();
					}

					htmldir = new File(rootdir, "html");
					if(!htmldir.exists()) {
						htmldir.mkdirs();
					}
					
					break;
				case "giturl":
					println("%s = %s",key,preferences.getString(key, ""));
					break;
				case "xmlurl":
					println("%s = %s",key,preferences.getString(key, ""));
					break;
				case "repos":
					println("%s:",key);
					for(String repo : preferences.getString(key, "").trim().split("\n")) {
						println(" + %s",repo);
					}
					break;
				default:
					println("%s",key);
					break;
			}
		} catch (Exception e) {
			print(e);
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

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		try {
			switch (item.getItemId()) {
				case R.id.LOGIN:
					authenticateDialog();
					break;
				case R.id.PREFERENCES:
					startPreferencesActivity();
					break;
				case R.id.REPOS:
					reposDialog();
					break;
				case R.id.DOWNLOAD:
					stack.execute(new Download(), preferences.getString("repos", "").trim().split("\n"));
					break;
				case R.id.IMPORT:
					stack.execute(new Import(), preferences.getString("repos", "").trim().split("\n"));
					break;
				case R.id.QUERY:
					stack.execute(new Query(), preferences.getString("repos", "").trim().split("\n"));
					break;
				case R.id.LOAD:
					stack.execute(new Load(), preferences.getString("repos", "").trim().split("\n"));
					break;
				default:
					return super.onOptionsItemSelected(item);
			}
		} catch(Exception e) {
			print(e);
		}
		return true;
	}
	
	private void startPreferencesActivity() {
		try {
			Intent intent = new Intent(this, MyPreferencesActivity.class);
			startActivity(intent);
		} catch(Exception e) {
			print(e);
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
	
	private class Load extends AsyncTask<String, Integer, String> 
	{
		private static final String TAG = "Load";

		@Override
		protected String doInBackground(String... args)
		{
			println(TAG+"...");
			progress.setMax(1);
			File file = writeIndex(args);
			if(file!=null) {
				command("load file://"+file.getAbsolutePath());
			} else {
				print(new Error("no index.html"));
			}
			publishProgress(1);
			return "ok";
		}

        @Override
        protected void onPreExecute() {
			progress.setVisibility(ProgressBar.VISIBLE);
			super.onPreExecute();
		}

        @Override
        protected void onPostExecute(String result) {
            //println(TAG+" "+result);
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onPostExecute(result);
        }

		@Override
		protected void onCancelled() {
			println(TAG+" "+"cancelled!");
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onCancelled();
		}

        @Override
        protected void onProgressUpdate(Integer... status) {
			super.onProgressUpdate(status);
			progress.setProgress(status[0]);
		}

		private File writeIndex(String[] names) {
			try {
				File file = new File(htmldir, "index.html");
				MyFileWriter writer = new MyFileWriter(file);
				writer.println("<!DOCTYPE html>");
				writer.println("<html>");
				writer.println("<head>");
				writer.println("<title>list</title>");
				writer.println("</head>");
				writer.println("<body>");
				for(final String name : names) {
					writer.println("<li><a href='"+name+".html'>"+name+"</a></li>");
				}
				writer.println("</body>");
				writer.println("</html>");
				writer.close();
				return file;
			} catch (Exception e) {
				print(e);
				return null;
			}
		}
	}

	private class Download extends AsyncTask<String, Integer, String> 
	{
		private static final String TAG = "Download";
		
		@Override
        protected String doInBackground(String... args) {
			if(db==null || xml==null) {
				return "fail";
			}
			println(TAG+"...");
			
			progress.setMax(args.length);
			for(int i=0; i<args.length; i++) {
				println(TAG+" "+args[i]+" ... ");
				
				try {
					db.download(args[i], "dev");
				} catch (Exception e) {
					print(e);
				}

				try {
					xml.download(args[i]);
				} catch (Exception e) {
					print(e);
				}
				
				publishProgress(i);
			}
			
            return "ok";
        }

        @Override
        protected void onPreExecute() {
			progress.setVisibility(ProgressBar.VISIBLE);
			super.onPreExecute();
		}

        @Override
        protected void onPostExecute(String result) {
            println(TAG+" "+result);
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onPostExecute(result);
        }

		@Override
		protected void onCancelled() {
			println(TAG+" "+"cancelled!");
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onCancelled();
		}

        @Override
        protected void onProgressUpdate(Integer... status) {
			super.onProgressUpdate(status);
			progress.setProgress(status[0]);
		}
    }

	private class Import extends AsyncTask<String, Integer, String>
	{
		private static final String TAG = "Import";
		
		@Override
        protected String doInBackground(String... args) {
			if(db==null) {
				return "fail";
			}
			println(TAG+"...");
			progress.setMax(args.length);
			for(int i=0;i<args.length;i++) {
				println(TAG+" "+args[i]+" ... ");
				db.importSQL(args[i]);
				publishProgress(i);
			}
            return "ok";
        }

        @Override
        protected void onPreExecute() {
			progress.setVisibility(ProgressBar.VISIBLE);
			super.onPreExecute();
		}

        @Override
        protected void onPostExecute(String result) {
            println(TAG+" "+result);
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onPostExecute(result);
        }

		@Override
		protected void onCancelled() {
			println(TAG+" "+"cancelled!");
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onCancelled();
		}

        @Override
        protected void onProgressUpdate(Integer... status) {
			super.onProgressUpdate(status);
			progress.setProgress(status[0]);
		}
    }
	
	private class Query extends AsyncTask<String, Integer, String>
	{
		private static final String TAG = "Query";
		
		@Override
		protected String doInBackground(String... args)
		{
			println(TAG+"...");
			progress.setMax(args.length);
			publishProgress(0);
			
			File file = writeIndex(args);
			if(file!=null) {
				command("load file://"+file.getAbsolutePath());
			}

			for(int i = 0; i<args.length; i++) {
				println(TAG+" "+args[i]+" ... ");
				
				try {
					Specification specification = new Specification();
					specification.query(args[i]);
					specifications.put(args[i], specification);
				} catch (Exception e) {
					print(e);
				}

				try {
				 	Results results = new Results();
					results.read(args[i]);
					testResults.put(args[i], results);
				} catch (Exception e) {
					print(e);
				}

				try {
					Item tree = populate(args[i]);
					writeTree(args[i], tree);
					writeRequirements(args[i], tree);
					writeDetails(args[i], tree);
					writeResults(args[i], tree);
				} catch(Exception e) {
					print(e);
				}

				publishProgress(i);
			}
			return "ok";
		}

        @Override
        protected void onPreExecute() {
			progress.setVisibility(ProgressBar.VISIBLE);
			super.onPreExecute();
		}

        @Override
        protected void onPostExecute(String result) {
            println(TAG+" "+result);
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onPostExecute(result);
        }

		@Override
		protected void onCancelled() {
			println(TAG+" "+"cancelled!");
			progress.setVisibility(ProgressBar.INVISIBLE);
			stack.completed(this);
			super.onCancelled();
		}
		
		@Override
        protected void onProgressUpdate(Integer... value) {
			super.onProgressUpdate(value);
			progress.setProgress(value[0]);
		}
		
		private void writeResults(String param, MainActivity.Item tree)
		{
			try {
				File file = new File(htmldir, param+"-results.html");
				MyFileWriter html = new MyFileWriter(file);
				html.println("<!DOCTYPE html>");
				html.println("<html>");
				html.println("<head>");
				html.println("<title>"+param+" - details</title>");
				html.println("</head>");
				html.println("<body>");

				Item projects = tree.getChild("projects");
				for(Item project : projects.getChildren()) {
					html.println("<H1 id='project-%s'>project: %s</H1>", project.getName(), project.getName());

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
				html.println("</body>");
				html.println("</html>");
				html.close();
			} catch (FileNotFoundException e) {
				print(e);
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
				File file = new File(htmldir, param+"-details.html");
				MyFileWriter html = new MyFileWriter(file);
				html.println("<!DOCTYPE html>");
				html.println("<html>");
				html.println("<head>");
				html.println("<title>"+param+" - details</title>");
				html.println("</head>");
				html.println("<body>");
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
								print(e);
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
				html.println("</body>");
				html.println("</html>");
				html.close();
			} catch (FileNotFoundException e) {
				print(e);
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

		private void writeManualScenario(String param, Item scenario)
		{
			try {
				File file = new File(htmldir, param+"-scenario-"+scenario.getName()+".html");
				MyFileWriter html = new MyFileWriter(file);
				html.println("<!DOCTYPE html>");
				html.println("<html>");
				html.println("<head>");
				html.println("<title>%s - scenario: %s</title>", param, scenario.getName());
				html.println("</head>");
				html.println("<body>");

				html.println(htmlValidateManualScenarioResultsJS(param, scenario));

				html.println("<form id='form' onsubmit='return checkResults()' method='post'>");

				html.println("<H3 id='scenario-%s'>scenario: %s = %s</H3>", scenario.getName(), scenario.getName(), scenario.getProperty("TITLE"));

				Properties scenarioProperties = scenario.getProperties();

				html.println("<p><b>%s:</b> %s</p>", "TYPE", scenarioProperties.getProperty("SCENARIOTYPE"));
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
									String stepVerdict = procedure.getName()+"-"+step.getProperty("STEPNUMBER");
									html.println("<P>%s</P>", htmlFormRadio("stepVerdict", stepVerdict, new String[] {"PASS","FAIL"}));
								}
								html.println("</OL>");
							}
						}
					} catch(Exception e) {
						print(e);
					}

					Item testcases = procedure.getChild("testcases");
					if(testcases!=null) {
						html.println("<P><B>TEST CASES:</B></P>");
						html.println("<OL>");
						for(Item testcase : testcases.getChildren()) {
							String title = specifications.get(param).get(Specification.TESTCASES, testcase.getName()).getProperty("TITLE");
							html.println("<LI>testcase:  <A HREF='#testcase-%s'>%s</A> = %s</LI>", testcase.getName(), testcase.getName(), title);
							html.println("<P>%s</P>", htmlFormRadio("testcaseVerdict", testcase.getName(), new String[] {"PASS","FAIL"}));
						}
						html.println("</OL>");
					}

				}
				html.println("<input type='submit' name='submit' value='Submit' />");
				html.println("</form>");

				html.println("</body>");
				html.println("</html>");
				html.close();
			} catch (FileNotFoundException e) {
				print(e);
			}
		}

		private void writeRequirements(String param, MainActivity.Item tree) {
			try {
				File file = new File(htmldir, param+"-requirements.html");
				MyFileWriter html = new MyFileWriter(file);
				html.println("<!DOCTYPE html>");
				html.println("<html>");
				html.println("<head>");
				html.println("<title>"+param+" - requirements</title>");
				html.println("</head>");
				html.println("<body>");
				html.println("<H1>"+param+" - requirements</H1>");

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

				html.println("</body>");
				html.println("</html>");
				html.close();
			} catch (FileNotFoundException e) {
				print(e);
			}
		}

		private void writeTree(String name, Item tree) {
			try {
				File file = new File(htmldir, name+".html");
				MyFileWriter html = new MyFileWriter(file);
				html.println("<!DOCTYPE html>");
				html.println("<html>");
				html.println("<head>");
				html.println("<title>"+name+"</title>");
				html.println(HTML.treeStyle());
				html.println(HTML.script());
				html.println("</head>");
				html.println("<body>");
				html.println("<H1>"+name+"</H1>");
				printitem(html, tree);
				html.println("</body>");
				html.println("</html>");
				html.close();
			} catch (FileNotFoundException e) {
				print(e);
			}
		}

		private File writeIndex(String[] params) {
			File file = null;
			try {
				file = new File(htmldir, "index.html");
				MyFileWriter html = null;
				try {
					html = new MyFileWriter(file);
					html.println("<!DOCTYPE html>");
					html.println("<html>");
					html.println("<head>");
					html.println("<title>list</title>");
					html.println("</head>");
					html.println("<body>");
					for(final String param : params) {
						html.println("<li><a href='"+param+".html'>"+param+"</a></li>");
					}
					html.println("</body>");
					html.println("</html>");
				} catch (Exception e) {
					print(e);
				} finally {
					html.close();
				}
			} catch (Exception e) {
				print(e);
			}
			return file;
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

			if(verbose) println("- baselines:");
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

			if(verbose) println("- deployments:");
			Item deployments = new Item(tree, null, "deployments", null);
			for (String deploymentID : specification.list(Specification.DEPLOYMENTS))
			{
				Properties deploymentProperties = specification.get(Specification.DEPLOYMENTS, deploymentID);
				Item deployment = new Item(deployments, "deployment", deploymentID, deploymentProperties);
			}

			if(verbose) println("- information:<br>");
			Item information = new Item(tree, null, "information", null);
			for (String infoID : specification.list(Specification.INFORMATION))
			{
				Properties infoProperties = specification.get(Specification.INFORMATION, infoID);
				Item info = new Item(information, "info", infoID, infoProperties);
			}

			if(verbose) println("- performance:");
			Item performance = new Item(tree, null, "performance", null);
			for (String measurementID : specification.list(Specification.PERFORMANCE))
			{
				Properties measurementProperties = specification.get(Specification.PERFORMANCE, measurementID);
				Item measurement = new Item(performance, "measurement", measurementID, measurementProperties);
			}

			if(verbose) println("- requirements:");
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

			if(verbose) println("- projects:");
			Item projects = new Item(tree, null, "projects", null);
			for (String projectID : specification.list(Specification.PROJECTS))
			{
				Properties projectProperties = specification.get(Specification.PROJECTS, projectID);
				Item project = new Item(projects, "project", projectID, projectProperties);

				if(verbose) println("- - requirements:");
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

				if(verbose) println("- - testareas:");
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

				if(verbose) println("- - scenarios:");
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
							print(e);
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
	
	class Specification {

		public boolean showdata;

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

		public void query(String name) {
			db.connect(name);
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
					if(showdata) println("baselines:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String key = result.getProperty("NAME");
						if(mapBaselinePK.containsValue(key)) {
							continue;
						}
						mapBaselinePK.put(pk, key);
						baseline.put(key, result);
						if(showdata) println("<li>baseline = "+key);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("baseline-item:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String key = result.getProperty("ID");
						items.put(key, result);
						if(showdata) println("<li>item = "+key);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("deployments:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String key = result.getProperty("NAME");
						if(mapDeploymentPK.containsValue(key)) {
							continue;
						}
						mapDeploymentPK.put(pk, key);
						deployments.put(key, result);
						if(showdata) println("<li>deployment = "+key);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("information:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String key = result.getProperty("KEY");
						if(mapInformationPK.containsValue(key)) {
							continue;
						}
						mapInformationPK.put(pk, key);
						information.put(key, result);
						if(showdata) println("<li>info = "+key);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("performance:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String key = result.getProperty("KEY");
						if(mapPerformancePK.containsValue(key)) {
							continue;
						}
						mapPerformancePK.put(pk, key);
						performance.put(key, result);
						if(showdata) println("<li>measurement = "+key);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("requirements:<br>");
					if(showdata) println("<ol>");
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
						if(showdata) println("<li>requirement = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("user-requirements:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String id = result.getProperty("ID");
						if(requirements.containsKey(id)) {
							continue;
						}
						requirements.put(id, result);
						if(showdata) println("<li>user-requirement = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("software-requirements:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String id = result.getProperty("ID");
						if(requirements.containsKey(id)) {
							continue;
						}
						requirements.put(id, result);
						if(showdata) println("<li>software-requirement = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("projects:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						mapProjectPK.put(pk, id);
						projects.put(id, result);
						if(showdata) println("<li>project = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("requirements:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("REQUIREMENT_ID");
						mapRequirementPK.put(pk, id);
						requirements.put(id, result);
						if(showdata) println("<li>requirement = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("testareas:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						mapTestareaPK.put(pk, id);
						testareas.put(id, result);
						if(showdata) println("<li>testarea = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("features:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						// I am forced to fix the feature ID
						id = mapTestareaPK.get(result.getProperty("TESTAREA_PK"))+"-"+id;
						result.setProperty("ID", id);
						mapFeaturePK.put(pk, id);
						features.put(id, result);
						if(showdata) println("<li>feature = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
					if(showdata) println("testcases:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						mapTestCasePK.put(pk, id);
						testcases.put(id, result);
						if(showdata) println("<li>testcase = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
				print(e);
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
					if(showdata) println("scenarios:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						mapScenarioPK.put(pk, id);
						scenarios.put(id, result);
						if(showdata) println("<li>scenario = "+id);

						// PATCH
						String tapk = result.getProperty("TESTAREA_PK");
						String testareaID = mapTestareaPK.get(tapk);
						if(testareaID!=null) {
							result.put("TESTAREA_ID", testareaID);
						}

					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
							print(new Exception(String.format("additional information pk %s not found!",pk)));
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
				print(e);
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
							print(new Exception(String.format("scenario measurement pk %s not found!",pk)));
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
				print(e);
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
							print(new Exception(String.format("scenario deployment pk %s not found!",pk)));
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
				print(e);
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
					if(showdata) println("procedures:<br>");
					if(showdata) println("<ol>");
					for(Properties result : results) {
						String pk = result.getProperty("PK");
						String id = result.getProperty("ID");
						// I am forced to fix the procedure ID
						id = mapScenarioPK.get(result.getProperty("SCENARIO_PK"))+"-"+id;
						result.setProperty("ID", id);
						mapProcedurePK.put(pk, id);
						procedures.put(id, result);
						if(showdata) println("<li>procedure = "+id);
					}
					if(showdata) println("</ol>");
				}
			} catch (Exception e) {
				print(e);
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
				print(e);
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
				print(e);
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
				print(e);
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

		public void read(String name) {
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
				print(e);
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
									print(new Warning("spaces before/after the testcase (%s)",testcase));
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
	
	class Stack {

		private boolean running = false;
		private Vector<Entry> tasks = new Vector<>();
		
		class Entry {
			private AsyncTask task;
			private Object[] args;

			public Entry(AsyncTask task , Object... args) {
				this.task = task;
				this.args = args;
			}

			public void execute() {
				task.execute(args);
			}
		}

		public void execute(AsyncTask task, Object... args) {
			tasks.add(new Entry(task, args));
			if(!running) {
				running = true;
				task.execute(args);
			}
		}
		
		public void completed(AsyncTask task) {
			webView.scrollTo(0, webView.getContentHeight());
			Iterator<Entry> i = tasks.iterator();
			Entry next = null;
			while(i.hasNext()) {
				next = i.next();
				if(!next.task.equals(task)) 
					break;
				tasks.remove(next);
				next=null;
			}
			if(next!=null){
				next.execute();
			} else {
				running=false;
			}
		}
	}
	
	public class XML {

		public XML() {
		}

		private String getURL(String name) {
			return String.format("%s/%s-val-results.xml", preferences.getString("xmlurl", "."), name);
		}

		private String getPath(String name) {
			return new File(xmldir, String.format("%s-val-results.xml", name)).getAbsolutePath();
		}

		public String download(String name) throws ConnectException {
			try {
				String from = getURL(name);
				String to = getPath(name);
				if(verbose) {
					println("downloading %s", name);
					println("  from %s", from);
					println("  to   %s", to);
				}
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
					print(new Error("download: UNAUTHORIZED (%s)!",name));
				} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
					print(new Error("download: BAD REQUEST (%s)!",name));
				} else  if(e.getMessage().startsWith("Connection timed out: connect")) {
					throw new ConnectException(e.getMessage());
				} else {
					print(e);
				}
				return null;
			}
		}

		public Document getDocument(String name) {
			Document doc = null;
			try {
				String path = getPath(name);
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setIgnoringComments(false);
				factory.setIgnoringElementContentWhitespace(false);
				factory.setValidating(false);
				DocumentBuilder builder = factory.newDocumentBuilder();
				doc = builder.parse(new InputSource("file:"+path));
			} catch (Exception e) {
				print(e);
			}
			return doc;
		}
	}
	
	public class DB {

		private static final String DRIVER = "org.h2.Driver";
		private static final String JDBC   = "jdbc:h2:";
		private static final String USER   = "SA";
		private static final String PSWD   = "";

		public Map<String, Connection> connections = new HashMap<>();

		public DB() throws ClassNotFoundException {
			Class.forName(DRIVER);
		}

		private String getURL(String name, String branch) {
			return String.format("%s/%s.git/%s/vtest!db!%s-val-spec.sql", preferences.getString("giturl", "."), name, branch, name);
		}

		private String getPath(String name) {
			return new File(sqldir, String.format("%s-val-spec.sql", name)).getAbsolutePath();
		}

		public String download(String name, String branch) throws ConnectException {
			String from = getURL(name, branch);
			String to   = getPath(name);
			if(verbose) {
				println("downloading %s ,,,<br>", name);
				println("  from %s", from);
				println("  to   %s", to);
			}

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
					println("downloading ÷s not authorized!", name);
				} else  if(e.getMessage().startsWith("Server returned HTTP response code: 400")) {
					println("downloading ÷s bad request!", name);
				} else  if(e.getMessage().startsWith("Connection timed out: connect")) {
					throw new ConnectException(e.getMessage());
				} else {
					print(e);
				}
				return null;
			}
			return to;
		}

		public void importSQL(String name) {
			try {
				String path = getPath(name);
				String from = path.substring(0, path.indexOf(name));
				String to = JDBC + from + name;
				String username = USER;
				String password = PSWD;
				
				DeleteDbFiles.execute(from, name, true);
				
				if(verbose) {
					println("importing %s", name);
					println("  from %s", from);
					println("  to   %s", to);
				}

				Charset charset = null;
				boolean continueOnError = true;
				RunScript.execute(to, username, password, path, charset, continueOnError);
			} catch(SQLException e) {
				print(e, "(importing SQL for %s)", name);
			}
		}

		public Connection connect(String name) {
			if(connections.containsKey(name))
				return connections.get(name);
			String path = getPath(name);
			String dbpath = path.substring(0, path.lastIndexOf(name));
			Connection connection = null;
			try {
				String url = JDBC + dbpath + name;
				String username = USER;
				String password = PSWD;
				connection = DriverManager.getConnection(url+";AUTO_SERVER=TRUE", username, password);
				connections.put(name, connection);
			} catch (SQLException e) {
				print(e);
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
				print(e);
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
				print(e, "(executing query \"%s\")", query);
			} catch (Exception e) {
				print(e, "(executing query \"%s\")", query);
			}
			if(closeafter) {
				disconnect(name);
			}
			return results;
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
	
	public class MyWebAppInterface {

		Context context;
		
		public MyWebAppInterface(Context c) {
			context = c;
		}

		@JavascriptInterface
		public void showToast(String toast) {
			Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
		}
		
	}
	
	public class MyWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
	}
	
	public class HtmlHandler extends Handler {

		private WebView view;
		private StringBuffer buffer = new StringBuffer();

		public HtmlHandler(WebView view) {
			super(Looper.getMainLooper());
			this.view = view;
		}

		@Override
		public void handleMessage(Message message) {
			switch(message.what) {
				case 0:
					if(message.obj instanceof String) {
						String[] args = ((String)message.obj).split(" ");
						switch (args[0]) {
							case "clear":
								if(buffer.length()>0) {
									buffer.delete(0,buffer.length());
									view.loadData(buffer.toString(), "text/html", "UTF-8");
								}
								break;
							case "load":
								if(args.length>1) {
									view.loadUrl(args[1]);
								}
								break;
							default:
								break;
						}
					}
					break;
				case 1:
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
	
	public void command(String s) {
		print("command..."+s);
		handler.obtainMessage(0, s).sendToTarget();
	}

	public void print(String fmt, Object... args) {
		String s = String.format(fmt, args);
		handler.obtainMessage(1, s).sendToTarget();
	}

	public void println(String fmt, Object... args) {
		print(fmt+"<br>\n", args);
	}

	public void println() {
		println("");
	}

	public void print(Exception e, String fmt, Object... args) {
		String s = String.format(fmt, args);
		String color = (e instanceof Warning) ? "orange" : "red";
		println("<span style='color:%s'>%s %s %s</span>", color, e.getClass().getSimpleName(), e.getMessage(), s);
		if(verbose) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			println("<pre style='color:%s'>%s</pre>", color, sw.getBuffer().toString());
		}
	}

	public void print(Exception e) {
		print(e, "");
	}
	
	class Settings {
		final String TAG = "Settings";
		final Properties properties = new Properties();
		final File file = new File(rootdir, ".times.xml");

		void load() {
			try {
				if(verbose) println(TAG+".load()..."+file.getAbsolutePath());
				properties.loadFromXML(new FileInputStream(file));
			} catch (Exception e) {
				print(e);
			}
		}

		void save() {
			try {
				if(verbose) println(TAG+".save()..."+file.getAbsolutePath());
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				properties.storeToXML(new FileOutputStream(file), sdf.format(System.currentTimeMillis()));
			} catch (Exception e) {
				print(e);
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
	
	public void authenticateDialog() {
		LayoutInflater layout = LayoutInflater.from(this);
        View view = layout.inflate(R.layout.login, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        final EditText user = view.findViewById(R.id.USERNAME);
        final EditText pass = view.findViewById(R.id.PASSWORD);
		builder.setTitle("LOGIN");
        builder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					try {
						final String password = pass.getText().toString();
						final String username = user.getText().toString();
						if(username==null || password==null) { 
							Toast.makeText(MainActivity.this,"Invalid username or password", Toast.LENGTH_LONG).show();
							authenticateDialog();
							return;
						}
						authenticate(username, password);
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

	public void authenticate(final String user, final String pswd) {
		Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(user, pswd.toCharArray());
				}
			});
		MenuItem item = menu.findItem(R.id.LOGIN);
		item.setIcon(getResources().getDrawable(R.drawable.user2));
	}
	
}
