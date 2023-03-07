/*
 * Copyright (C) 2023 GDSC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package uk.ac.sussex.gdsc.smlm.ij.example.plugins;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.smlm.data.config.ResultsProtos.ResultsTableSettings;
import uk.ac.sussex.gdsc.smlm.ij.example.analysis.Localisations;
import uk.ac.sussex.gdsc.smlm.ij.gui.PeakResultTableModel;
import uk.ac.sussex.gdsc.smlm.ij.gui.PeakResultTableModelFrame;
import uk.ac.sussex.gdsc.smlm.ij.plugins.ResultsManager;
import uk.ac.sussex.gdsc.smlm.ij.plugins.ResultsManager.InputSource;
import uk.ac.sussex.gdsc.smlm.results.MemoryPeakResults;

/**
 * View a localisation dataset held in memory.
 *
 * <p>Note: An underscore is useful in any {@link PlugIn} or
 * {@link ij.plugin.filter.PlugInFilter PlugInFilter} as exceptions that are raised within
 * the plugin are caught and displayed in a plugin-specific manner. The underscore allows
 * ImageJ to know the exception occurred in a plugin class.
 */
public class View_Localisations implements PlugIn {
  /** Plugin title. */
  private static final String TITLE = "View Localisations";
  /** Counter for random datasets. */
  private static final AtomicInteger dataset = new AtomicInteger();

  // Here we persist settings between invocations of the plugin using 'static' members.

  /** Results table settings. Note that this settings object is immutable. It is updated
   * using a builder to a new instance so is safe to keep a static reference. */
  private static ResultsTableSettings resultsTableSettings = ResultsTableSettings.getDefaultInstance();
  /** Input dataset name. */
  private static String input = "";

  // Settings used only by the instance. We could reuse the static members but keeping
  // an instance copy allows them to be changed without affecting the persistence.

  /** Dataset name. */
  private String name;

  @Override
  public void run(String arg) {
    if (showDialog()) {
      viewLocalisations();
    }
  }

  private boolean showDialog() {
    // Use the specialised GDSC SMLM input dialog to list the available datasets.
    // (This adds functionality to ij.gui.GenericDialog but is used in the same way.)
    ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    ResultsManager.addInput(gd, input, InputSource.MEMORY);

    // Other options here
    gd.addMessage("Create a random dataset and ignore the input option");
    gd.addCheckbox("Create_random", false);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    // Get entered values
    // Obtain the dataset name using the specialised method to parse the displayed name
    input = name = ResultsManager.getInputSource(gd);
    // Other options
    if (gd.getNextBoolean()) {
      MemoryPeakResults results = createRandomResults();
      MemoryPeakResults.addResults(results);
      // Switch display to the new random dataset (ignores the selected input)
      input = name = results.getName();
    }

    return true;
  }

  private void viewLocalisations() {
    MemoryPeakResults results = ResultsManager.loadInputResults(name, false, null, null);
    if (MemoryPeakResults.isEmpty(results)) {
      IJ.error(TITLE, "No results could be loaded");
      return;
    }
    PeakResultTableModel model = new PeakResultTableModel(results, true, resultsTableSettings);
    PeakResultTableModelFrame frame = new PeakResultTableModelFrame(model);
    frame.setTitle(results.getName());
    frame.setVisible(true);
    // Save changes to the interactive table settings
    model.addSettingsUpdatedAction(s -> resultsTableSettings = s);
  }

  /**
   * Main method for debugging.
   *
   * For debugging, it is convenient to have a method that starts ImageJ and calls the plugin, e.g.
   * after setting breakpoints.
   *
   * @param args unused
   * @throws URISyntaxException if the URL cannot be converted to a URI
   */
  public static void main(String[] args) throws URISyntaxException {
    // Set the base directory for plugins
    // see: https://stackoverflow.com/a/7060464/1207769
    Class<View_Localisations> clazz = View_Localisations.class;
    java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
    File file = new File(url.toURI());
    // Note: This returns the base path. ImageJ will find plugins in here that have an
    // underscore in the name. But it will not search recursively through the
    // package structure to find plugins. Adding this at least puts it on ImageJ's
    // classpath so plugins not satisfying these requirements can be loaded.
    System.setProperty("plugins.dir", file.getAbsolutePath());

    // Start ImageJ and exit when closed
    ImageJ imagej = new ImageJ();
    imagej.exitWhenQuitting(true);

    // If this is in a sub-package or has no underscore then manually add the plugin
    String packageName = clazz.getName().replace(clazz.getSimpleName(), "");
    if (!packageName.isEmpty() || clazz.getSimpleName().indexOf('_') < 0) {
      // Add a spacer
      ij.Menus.installPlugin("", ij.Menus.PLUGINS_MENU, "-", "", IJ.getInstance());
      ij.Menus.installPlugin(clazz.getName(),
          ij.Menus.PLUGINS_MENU, clazz.getSimpleName().replace('_', ' '), "", IJ.getInstance());
    }

    // Initialise for testing, e.g. create some random datasets
    MemoryPeakResults.addResults(createRandomResults());
    MemoryPeakResults.addResults(createRandomResults());
    MemoryPeakResults.addResults(createRandomResults());

    // Run the plugin
    IJ.runPlugIn(clazz.getName(), "");
  }

  /**
   * Create a random set of localisations.
   *
   * <p>This is more involved than just creating XYZ values. The results are calibrated
   * so the values have a unit and the localisations are associated with a point spread function
   * (PSF). This allows the display of the localisations to be useful (e.g can change the
   * displayed units, show a SNR or a localisation precision estimate).
   *
   * @return the results
   */
  private static MemoryPeakResults createRandomResults() {
    // Use a utility class and provide the name for the dataset
    return Localisations.createRandomResults("Random" + dataset.incrementAndGet());
  }
}
