package com.linfords.swing.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.linfords.swing.util.LoggingClassLoader.Record.ClassLoaderEvent;

/**
 * @author slinford
 * 
 */
public final class LoggingClassLoader extends ClassLoader {
   // private final static AsyncPrinter aout = AsyncPrinter.defaultInstance();
   // private static final Log log =
   // LogFactory.getLog(LoggingClassLoader.class);

   private static final Logger logger = LoggerFactory
         .getLogger(LoggingClassLoader.class);
   public Level logLevel = Level.FINE;

   private static final String ANNOYING_ATTENTION_GRABBING_PREFIX = "";

   private final Record record = new Record();

   public void addObserver(Observer o) {
      record.addObserver(o);
   }

   public void deleteObserver(Observer o) {
      record.deleteObserver(o);
   }

   /**
    * 
    */
   public LoggingClassLoader() {
      super(LoggingClassLoader.class.getClassLoader());
      logger.log(
            logLevel,
            ANNOYING_ATTENTION_GRABBING_PREFIX
                  + " constructed with parent: "
                  + toStringClassLoaderChain(LoggingClassLoader.class
                        .getClassLoader()));
   }

   public static String toStringClassLoaderChain(final ClassLoader cl) {
      if (cl == null) {
         return null;
      }
      StringBuilder sb = new StringBuilder();
      StringBuilder indent = new StringBuilder();
      ClassLoader c = cl;
      while (c != null) {
         sb.append(indent).append(c);
         c = c.getParent();
         if (indent.length() == 0) {
            indent.append('\n');
         }
         indent.append("   ");
      }

      return sb.toString();
   }

   /**
    * Loads the class with the specified <a href="#name">binary name</a>. This method
    * searches for classes in the same manner as the {@link #loadClass(String, boolean)}
    * method. It is invoked by the Java virtual machine to resolve class references.
    * Invoking this method is equivalent to invoking {@link #loadClass(String, boolean)
    * <tt>loadClass(name,
    * false)</tt>}. </p>
    * 
    * @param name
    *        The <a href="#name">binary name</a> of the class
    * 
    * @return The resulting <tt>Class</tt> object
    * 
    * @throws ClassNotFoundException
    *         If the class was not found
    */
   public Class<?> loadClass(String name) throws ClassNotFoundException {
      record.recordLoadClass(name);
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "loadClass name(" + name + ")");
      return super.loadClass(name);
   }

   /**
    * Loads the class with the specified <a href="#name">binary name</a>. The default
    * implementation of this method searches for classes in the following order:
    * 
    * <p>
    * <ol>
    * 
    * <li>
    * <p>
    * Invoke {@link #findLoadedClass(String)} to check if the class has already been
    * loaded.
    * </p>
    * </li>
    * 
    * <li>
    * <p>
    * Invoke the {@link #loadClass(String) <tt>loadClass</tt>} method on the parent class
    * loader. If the parent is <tt>null</tt> the class loader built-in to the virtual
    * machine is used, instead.
    * </p>
    * </li>
    * 
    * <li>
    * <p>
    * Invoke the {@link #findClass(String)} method to find the class.
    * </p>
    * </li>
    * 
    * </ol>
    * 
    * <p>
    * If the class was found using the above steps, and the <tt>resolve</tt> flag is true,
    * this method will then invoke the {@link #resolveClass(Class)} method on the
    * resulting <tt>Class</tt> object.
    * 
    * <p>
    * Subclasses of <tt>ClassLoader</tt> are encouraged to override
    * {@link #findClass(String)}, rather than this method.
    * </p>
    * 
    * @param name
    *        The <a href="#name">binary name</a> of the class
    * 
    * @param resolve
    *        If <tt>true</tt> then resolve the class
    * 
    * @return The resulting <tt>Class</tt> object
    * 
    * @throws ClassNotFoundException
    *         If the class could not be found
    */
   protected synchronized Class<?> loadClass(String name, boolean resolve)
         throws ClassNotFoundException {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "loadClass name(" + name + ") resolve(" + resolve + ")");
      return super.loadClass(name, resolve);
   }

   /**
    * Finds the class with the specified <a href="#name">binary name</a>. This method
    * should be overridden by class loader implementations that follow the delegation
    * model for loading classes, and will be invoked by the {@link #loadClass
    * <tt>loadClass</tt>} method after checking the parent class loader for the requested
    * class. The default implementation throws a <tt>ClassNotFoundException</tt>. </p>
    * 
    * @param name
    *        The <a href="#name">binary name</a> of the class
    * 
    * @return The resulting <tt>Class</tt> object
    * 
    * @throws ClassNotFoundException
    *         If the class could not be found
    * 
    * @since 1.2
    */
   protected Class<?> findClass(String name) throws ClassNotFoundException {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + " findClass name(" + name + ")");
      return super.findClass(name);
   }

   /**
    * Finds the resource with the given name. A resource is some data (images, audio,
    * text, etc) that can be accessed by class code in a way that is independent of the
    * location of the code.
    * 
    * <p>
    * The name of a resource is a '<tt>/</tt>'-separated path name that identifies the
    * resource.
    * 
    * <p>
    * This method will first search the parent class loader for the resource; if the
    * parent is <tt>null</tt> the path of the class loader built-in to the virtual machine
    * is searched. That failing, this method will invoke {@link #findResource(String)} to
    * find the resource.
    * </p>
    * 
    * @param name
    *        The resource name
    * 
    * @return A <tt>URL</tt> object for reading the resource, or <tt>null</tt> if the
    *         resource could not be found or the invoker doesn't have adequate privileges
    *         to get the resource.
    * 
    * @since 1.1
    */
   public URL getResource(String name) {
      record.recordLoadResource(name);
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "getResource name(" + name + ")");
      return super.getResource(name);
   }

   /**
    * Finds all the resources with the given name. A resource is some data (images, audio,
    * text, etc) that can be accessed by class code in a way that is independent of the
    * location of the code.
    * 
    * <p>
    * The name of a resource is a <tt>/</tt>-separated path name that identifies the
    * resource.
    * 
    * <p>
    * The search order is described in the documentation for {@link #getResource(String)}.
    * </p>
    * 
    * @param name
    *        The resource name
    * 
    * @return An enumeration of {@link java.net.URL <tt>URL</tt>} objects for the
    *         resource. If no resources could be found, the enumeration will be empty.
    *         Resources that the class loader doesn't have access to will not be in the
    *         enumeration.
    * 
    * @throws java.io.IOException
    *         If I/O errors occur
    * 
    * @see #findResources(String)
    * 
    * @since 1.2
    */
   public Enumeration<URL> getResources(String name) throws IOException {
      record.recordLoadResource(name);
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "loadClass getResources(" + name + ")");
      return super.getResources(name);
   }

   /**
    * Finds the resource with the given name. Class loader implementations should override
    * this method to specify where to find resources. </p>
    * 
    * @param name
    *        The resource name
    * 
    * @return A <tt>URL</tt> object for reading the resource, or <tt>null</tt> if the
    *         resource could not be found
    * 
    * @since 1.2
    */
   protected URL findResource(String name) {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + " findResource name(" + name + ")");
      return super.findResource(name);
   }

   /**
    * Returns an enumeration of {@link java.net.URL <tt>URL</tt>} objects representing all
    * the resources with the given name. Class loader implementations should override this
    * method to specify where to load resources from. </p>
    * 
    * @param name
    *        The resource name
    * 
    * @return An enumeration of {@link java.net.URL <tt>URL</tt>} objects for the
    *         resources
    * 
    * @throws java.io.IOException
    *         If I/O errors occur
    * 
    * @since 1.2
    */
   protected Enumeration<URL> findResources(String name) throws IOException {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "findResources name(" + name + ")");
      return super.findResources(name);
   }

   /**
    * Returns an input stream for reading the specified resource.
    * 
    * <p>
    * The search order is described in the documentation for {@link #getResource(String)}.
    * </p>
    * 
    * @param name
    *        The resource name
    * 
    * @return An input stream for reading the resource, or <tt>null</tt> if the resource
    *         could not be found
    * 
    * @since 1.1
    */
   public InputStream getResourceAsStream(String name) {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "getResourceAsStream name(" + name + ")");
      return super.getResourceAsStream(name);
   }

   /**
    * Returns a <tt>Package</tt> that has been defined by this class loader or any of its
    * ancestors. </p>
    * 
    * @param name
    *        The package name
    * 
    * @return The <tt>Package</tt> corresponding to the given name, or <tt>null</tt> if
    *         not found
    * 
    * @since 1.2
    */
   protected Package getPackage(String name) {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "getPackage name(" + name + ")");
      return super.getPackage(name);
   }

   /**
    * Returns all of the <tt>Packages</tt> defined by this class loader and its ancestors.
    * </p>
    * 
    * @return The array of <tt>Package</tt> objects defined by this <tt>ClassLoader</tt>
    * 
    * @since 1.2
    */
   protected Package[] getPackages() {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "getPackages name()");
      return super.getPackages();
   }

   /**
    * Returns the absolute path name of a native library. The VM invokes this method to
    * locate the native libraries that belong to classes loaded with this class loader. If
    * this method returns <tt>null</tt>, the VM searches the library along the path
    * specified as the "<tt>java.library.path</tt>" property. </p>
    * 
    * @param libname
    *        The library name
    * 
    * @return The absolute path of the native library
    * 
    * @see System#loadLibrary(String)
    * @see System#mapLibraryName(String)
    * 
    * @since 1.2
    */
   protected String findLibrary(String libname) {
      logger.log(logLevel, ANNOYING_ATTENTION_GRABBING_PREFIX
            + "findLibrary libname(" + libname + ")");
      return super.findLibrary(libname);
   }

   // ////////////////////////////////////////////////////////////////

   public static final class Record extends Observable {
      private Map<String, ClassLoaderEvent> loadedClasses = new HashMap<String, ClassLoaderEvent>();
      private Map<String, ClassLoaderEvent> loadedResources = new HashMap<String, ClassLoaderEvent>();

      public void recordLoadClass(String name) {
         ClassLoaderEvent event = loadedClasses.get(name);
         if (event == null) {
            event = new ClassLoaderEvent(name,
                  ClassLoaderEvent.Type.ClassLoaded);
            loadedClasses.put(name, event);
         }
         event.count++;
         setChanged();
         notifyObservers(event);
      }

      public void recordLoadResource(String name) {
         ClassLoaderEvent event = loadedResources.get(name);
         if (event == null) {
            event = new ClassLoaderEvent(name,
                  ClassLoaderEvent.Type.ResourceLoaded);
            loadedResources.put(name, event);
         }
         event.count++;
         setChanged();
         notifyObservers(event);
      }

      public static final class ClassLoaderEvent {
         public static enum Type {
            ClassLoaded, ResourceLoaded, Unknown
         }

         private static final String NULL_STRING = "";

         public final String name;
         public final Type type;
         private int count = 0;

         private ClassLoaderEvent(String name, Type type) {
            name = (name == null) ? NULL_STRING : name;
            type = (type == null) ? Type.Unknown : type;
            this.name = name.trim();
            this.type = type;
         }

         @Override
         public boolean equals(Object obj) {
            if ((obj == null)
                  || ((obj instanceof ClassLoaderEvent) == false)) {
               return false;
            }

            ClassLoaderEvent rightHand = (ClassLoaderEvent) obj;

            return this.name.equals(rightHand.name)
                  && this.type.equals(rightHand.type);
         }

         @Override
         public int hashCode() {
            return name.hashCode() + type.hashCode();
         }

         @Override
         public String toString() {
            return "ClassLoaderEvent name(" + name + ") type("
                  + type + ") count(" + count + ")";
         }
      }
   } // END class Record

   public static class ExampleRecordObserver implements Observer {

      @Override
      public void update(Observable classLoadingRecord, Object classLoaderEvent) {
         ClassLoaderEvent cle = (ClassLoaderEvent) classLoaderEvent;
         System.out.println(cle + " by thread '" + Thread.currentThread().getName() + "'");
         if (cle.count > 1) {
            new Exception(cle.name + " loaded " + cle.count + " times").printStackTrace();
         }
      }
   }

}
