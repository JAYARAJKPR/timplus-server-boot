package org.directtruststandards.timplus.server.springconfig;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;

import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.directtruststandards.timplus.cluster.routing.DelegatedRemotePacketRouterFactory;
import org.directtruststandards.timplus.common.crypto.KeyStoreProtectionManager;
import org.directtruststandards.timplus.server.monitor.PacketMonitor;
import org.directtruststandards.timplus.server.handlers.DomainCreationIQHandler;
import org.jivesoftware.openfire.OfflineMessageStrategy;
import org.jivesoftware.openfire.RemotePacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.domain.DomainManager;
import org.jivesoftware.openfire.filetransfer.proxy.FileTransferProxy;
import org.jivesoftware.openfire.handler.IQvCardHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.muc.spi.RemoteMUCCache;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.crl.impl.CRLRevocationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration
public class XMPPServerConfig
{
	private static final Logger LOGGER = LoggerFactory.getLogger(XMPPServerConfig.class);	
	
	protected static ApplicationContext ctx;
	
	protected static final String OPENFIRE_HOME_PROP = "openfireHome";
	
	protected static final String OPENFIRE_TEMPLATES_LOC = "/confTemplates/";

	protected static final String REMOTE_PACKET_ROUTER_NAME = "routing.clustering.remotepacketrouter.class";
	
	@Value("${openfire.home:.}")
	protected String openFireHome;
	
	@Value("${timplus.domain.name}")
	protected String domain;
	
	@Value("${timplus.adminUsername}")
	protected String adminUsername;
	
	@Value("${timplus.adminPassword}")
	protected String adminPassword;
	
	@Value("${timplus.filetransfer.proxy.host:}")
	protected String fileTransferProxyHost;
	
	@Value("${timplus.vcard.allowClientSet:false}")
	protected boolean vcardAllowClientSet;
	
	@Value("${timplus.secandtrust.crl.fileCacheLoc:}")
	protected String crlLoc;
	
	@Value("${timplus.secandtrust.crl.ignoreCLRChecking:false}")
	protected boolean ingoreCLRChecking;
	
	@Value("${timplus.server.enableClustering:false}")
	protected boolean enableClustering;
	
	@Value("${timplus.domain.allowClientCreation:false}")
	protected boolean allowClientDomainCreation;
	
	@Bean()
	@ConditionalOnMissingBean
	public XMPPServer xmppServer(ApplicationContext appCtx, PacketMonitor packetMonitor, KeyStoreProtectionManager keyStoreManager) throws Exception
	{
		ctx = appCtx;		
		
		System.setProperty(OPENFIRE_HOME_PROP, openFireHome);
		
		writeOpenFireConfig();
		
		writeSecurityConfig();
		
		writePlugins();
		
		// Setup configuration is generally not changed
		// These are specific settings to configure the server to
		// be compliant with the TIM+ plus
		writeSetupConfig();
		
		// These are options that can be changed per server instance
		// and are read from Spring configuration.
		configureOptions();
		
		// setup the packet intercepter for message monitoring
		InterceptorManager.getInstance().addInterceptor(packetMonitor);
		
		// setup the offline message listener
		OfflineMessageStrategy.addListener(packetMonitor);
		
		// setup the packet intercepter for presence information
		InterceptorManager.getInstance().addInterceptor(RemoteMUCCache.getInstance()); 
		
		final XMPPServer server = new XMPPServer(keyStoreManager);
		
		// Register custom domain creation IQ handler if enabled
		if (allowClientDomainCreation) {
			server.getIQRouter().addHandler(new DomainCreationIQHandler());
			LOGGER.info("Domain creation by clients is ENABLED");
		} else {
			LOGGER.info("Domain creation by clients is DISABLED");
		}
		
		LOGGER.info("Setting clustered configuration property to " + enableClustering);
		
		JiveGlobals.setProperty(ClusterManager.CLUSTER_PROPERTY_NAME, Boolean.toString(enableClustering));
		
		if (enableClustering)
		{
			LOGGER.info("Clustering is requested via configuration.  Setting up remote packet router and clustered caching.");
			
			// set up the remote packet router
			final String routerFactoryClassName = JiveGlobals.getProperty(REMOTE_PACKET_ROUTER_NAME, 
					"org.directtruststandards.timplus.cluster.routing.SCSDelegatedRemotePacketRouterFactory");
			
			try
			{
				final DelegatedRemotePacketRouterFactory packetRouterFactory = 
					(DelegatedRemotePacketRouterFactory)Class.forName(routerFactoryClassName).newInstance();
				
				final RemotePacketRouter remotePacketRouter = packetRouterFactory.getInstance();
				
				LOGGER.info("Remote packet router instance created.  Setting server routing table remote router to " + remotePacketRouter.getClass().toGenericString());

				
				server.getRoutingTable().setRemotePacketRouter(remotePacketRouter);
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Failed to create and set remote packet router.", e);
			}
			
			ClusterManager.startup();		
		}
		
		setAdminAcount();
		
		return server;
	}
	
	

	
	protected void 	writeOpenFireConfig() throws Exception
	{
		final File file = new File("conf/openfire.xml");
		
		if (!file.exists())
		{	
			String openFirePropString = IOUtils.resourceToString(OPENFIRE_TEMPLATES_LOC + "openfire.xml", Charset.defaultCharset());
		
			FileUtils.write(file, openFirePropString, Charset.defaultCharset());
		}
		else
		{
			// check to see if the admin console port has been shutdown
			String str = FileUtils.readFileToString(file, Charset.defaultCharset());
			str = str.replaceAll("<port>-1</port>","<port>9090</port>");
			str = str.replaceAll("<securePort>-1</securePort>","<securePort>9091</securePort>");
			
			FileUtils.writeStringToFile(file, str, Charset.defaultCharset(), false);
		}
	}
	
	protected void writeSecurityConfig() throws Exception
	{
		final File file = new File("conf/security.xml");
		
		if (!file.exists())
		{
			String securityFilePropString = IOUtils.resourceToString(OPENFIRE_TEMPLATES_LOC + "security.xml", Charset.defaultCharset());
		
			FileUtils.write(file, securityFilePropString, Charset.defaultCharset());
		
			CopyResourcesFromClassPathToFilesystemDirectory("resources/security", "./resources/security");
		}
	}
	
	protected void writePlugins() throws Exception
	{			
		CopyResourcesFromClassPathToFilesystemDirectory("plugins", "./plugins");		
	}
	
	protected void CopyResourcesFromClassPathToFilesystemDirectory(String source, String dest) throws Exception
	{
		final File checkSource = new File(source);
		final File directory = new File(dest);
		
		if (!checkSource.exists())
			directory.mkdirs();
		
		FileUtils.cleanDirectory(directory);
		
		final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        final Resource[] resources = resolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + source + "/**");
        for (Resource resource : resources) 
        {
            if (resource.exists() & resource.isReadable() && resource.contentLength() > 0) 
            {
                final URL url = resource.getURL();
                final String urlString = url.toExternalForm();
                final String targetName = urlString.substring(urlString.indexOf(source));
                File destination = new File(targetName);
                FileUtils.copyURLToFile(url, destination);
            } 
        }
        
	}
	
	protected void writeSetupConfig()
	{
        
		
        JiveGlobals.setHomeDirectory(".");

        JiveGlobals.setConfigName("conf/openfire.xml");
		
        JiveGlobals.setXMLProperty("connectionProvider.className",
                "org.directtruststandards.timplus.server.database.DatasourceConnectionProvider");


        final boolean isInitialSetup = !Boolean.parseBoolean(JiveGlobals.getProperty("initialSetup"));
        
        if (isInitialSetup)
        {
        	/*
        	 * Some default domain information
        	 */
	        JiveGlobals.setXMLProperty("xmpp.domain", domain);
	        JiveGlobals.setXMLProperty("xmpp.fqdn", domain);
	        JiveGlobals.migrateProperty("xmpp.domain");
	        JiveGlobals.migrateProperty("xmpp.fqdn");
	        
	        /*
	         * Provider implementation classes
	         */
	        JiveGlobals.setProperty("provider.auth.className", JiveGlobals.getXMLProperty("provider.auth.className",
	                org.jivesoftware.openfire.auth.DefaultAuthProvider.class.getName()));
	        JiveGlobals.setProperty("provider.user.className", JiveGlobals.getXMLProperty("provider.user.className",
	                org.jivesoftware.openfire.user.DefaultUserProvider.class.getName()));
	        JiveGlobals.setProperty("provider.group.className", JiveGlobals.getXMLProperty("provider.group.className",
	                org.jivesoftware.openfire.group.DefaultGroupProvider.class.getName()));
	        JiveGlobals.setProperty("provider.vcard.className", JiveGlobals.getXMLProperty("provider.vcard.className",
	                org.jivesoftware.openfire.vcard.DefaultVCardProvider.class.getName()));
	        JiveGlobals.setProperty("provider.lockout.className", JiveGlobals.getXMLProperty("provider.lockout.className",
	                org.jivesoftware.openfire.lockout.DefaultLockOutProvider.class.getName()));
	        JiveGlobals.setProperty("provider.securityAudit.className", JiveGlobals.getXMLProperty("provider.securityAudit.className",
	                org.jivesoftware.openfire.security.DefaultSecurityAuditProvider.class.getName()));
	        JiveGlobals.setProperty("provider.admin.className", JiveGlobals.getXMLProperty("provider.admin.className",
	                org.jivesoftware.openfire.admin.DefaultAdminProvider.class.getName()));
	        
	        
	        /*
	         * Disable anonymous logins and encrypt passwords
	         */
	        JiveGlobals.setProperty("xmpp.auth.anonymous", "false");
	        JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");
	       
	        /*
	         * Set TLS security defaults
	         */
	        JiveGlobals.setProperty("xmpp.client.tls.policy", "required");
	        JiveGlobals.setProperty("xmpp.socket.ssl.client.protocols", "TLSv1.2");
	        JiveGlobals.setProperty("xmpp.server.tls.policy", "required");
	        JiveGlobals.setProperty("xmpp.socket.ssl.protocols", "TLSv1.2");
	        JiveGlobals.setProperty("xmpp.server.cert.policy", "needed");
	        
	        /*
	         * Don't allow inband registration
	         */
	        JiveGlobals.setProperty("register.inband", "false");
	        
	        /*
	         * Disable update notifications
	         */
	        JiveGlobals.setProperty("update.service-enabled", "false");
	        JiveGlobals.setProperty("update.notify-admins", "false");
	        
	        /*
	         * Enabled server to server compression option
	         */
	        JiveGlobals.setProperty("xmpp.server.compression.policy", "optional");
	        
	        /*
	         * Initial setup is complete
	         */
	        JiveGlobals.setProperty("initialSetup", "true");

        }
        
	}
	
	protected void configureOptions()
	{
		/*
		 * File transfer proxy settings
		 */
		if (!StringUtils.isEmpty(fileTransferProxyHost))
			JiveGlobals.setProperty( FileTransferProxy.PROPERTY_EXTERNALIP,  fileTransferProxyHost);
		else 
			JiveGlobals.setProperty( FileTransferProxy.PROPERTY_EXTERNALIP,  "");
		
		/*
		 * Determines if TIM+ end points can update vCard information
		 */
		JiveGlobals.setProperty( IQvCardHandler.PROPERTY_ALLOW_CLIENT_SET,  Boolean.toString(vcardAllowClientSet));
		
		/*
		 * Sets the CRL file cache location
		 */
		JiveGlobals.setProperty( CRLRevocationManager.PROPERTY_CRL_FILE_CACHE_LOC,  crlLoc);

		/*
		 * Sets the flag to ignore CRL checking
		 */
		JiveGlobals.setProperty( CRLRevocationManager.PROPERTY_CRL_IGNORE_CLR_CHECKING,  Boolean.toString(ingoreCLRChecking));

	}
	
	protected void setAdminAcount()
	{		
        try 
        {        	
    		//lets make sure our default domain has been added to the Domain manager
    		if (!DomainManager.getInstance().isRegisteredDomain(domain))
    			DomainManager.getInstance().createDomain(domain, true);        	
        	
        	if (adminUsername.indexOf("@") == -1)
        		adminUsername = (adminUsername + "@" + domain.toLowerCase());
    		
        	if (!UserManager.getInstance().isRegisteredUser(adminUsername))
        	{
            	final User adminUser = UserManager.getInstance().createUser(adminUsername, adminPassword, adminUsername, adminUsername + "@" + domain, domain);
                final Date now = new Date();
                adminUser.setCreationDate(now);
                adminUser.setModificationDate(now);
                
                AdminManager.getInstance().addAdminAccount(adminUsername, domain);       		
        	}
        	else if (adminUsername.contentEquals("admin"))
        	{

    			final User adminUser = UserManager.getInstance().getUser(adminUsername);
    			if (!adminUser.getEmail().equalsIgnoreCase(adminUsername + "@" + domain))
    			{
    				AdminManager.getInstance().removeAdminAccount(adminUsername, domain);
    				UserManager.getInstance().deleteUser(adminUser);
    				
                    final Date now = new Date();
                    adminUser.setCreationDate(now);
                    adminUser.setModificationDate(now);
                    
                    AdminManager.getInstance().addAdminAccount(adminUsername, domain);    
    				
    			}
        	}
    		
        	if (UserManager.getInstance().isRegisteredUser("admin") && !adminUsername.contentEquals("admin"))
    		{
    			final User adminUser = UserManager.getInstance().getUser("admin");
				AdminManager.getInstance().removeAdminAccount("admin", domain);
				UserManager.getInstance().deleteUser(adminUser);
    		}

        } 
        catch (Exception e) 
        {
        	// TODO: Log execption info
        	e.printStackTrace();
        }		
	}
	
	public static ApplicationContext getAppContext()
	{
		return ctx;
	}
}
