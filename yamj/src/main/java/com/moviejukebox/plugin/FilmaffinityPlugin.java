/*
 *      Copyright (c) 2004-2011 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list 
 *  
 *      Web: http://code.google.com/p/moviejukebox/
 *  
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *  
 *      For any reuse or distribution, you must make clear to others the 
 *      license terms of this work.  
 */

package com.moviejukebox.plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.poster.FilmAffinityPosterPlugin;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;

public class FilmaffinityPlugin extends ImdbPlugin {

    public static String FILMAFFINITY_PLUGIN_ID = "filmaffinity";
    private FilmAffinityPosterPlugin posterPlugin; // FIXME - Yaltar don't use PosterPlugin, but extract common method to an utility class.

    // Define plot length
    int preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");

    public FilmaffinityPlugin() {
        super();
        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "Spain");
        posterPlugin = new FilmAffinityPosterPlugin();
}

    @Override
    public boolean scan(Movie mediaFile) {
        boolean retval = true;
        String filmAffinityId = mediaFile.getId(FILMAFFINITY_PLUGIN_ID);
        if (StringTools.isNotValidString(filmAffinityId)) {
            filmAffinityId = posterPlugin.getId(mediaFile,mediaFile);
        } else {
            // Not already have the .html at the end ?
            if (filmAffinityId.indexOf(".html") == -1) {
                filmAffinityId += ".html";
            }
        }

        try {
            if (filmAffinityId.indexOf(".html") != -1) { // Update original title and plot (in Spanish)
                retval = updateFilmAffinityMediaInfo(mediaFile, filmAffinityId);
            }
        } catch (Exception ex) {
            System.out.println("bra");
        }

        // Fill in the rest of the fields from IMDB, taking care not to allow the title to get overwritten
        boolean overrideTitle = mediaFile.isOverrideTitle();
        mediaFile.setOverrideTitle(true);
        super.scan(mediaFile);
        mediaFile.setOverrideTitle(overrideTitle);

        return retval;
    }



    /**
     * Scan FilmAffinity html page for the specified movie
     */
    private boolean updateFilmAffinityMediaInfo(Movie movie, String filmAffinityId) {
        try {
            String xml = webBrowser.request("http://www.filmaffinity.com/es/film" + filmAffinityId, Charset.forName("ISO-8859-1"));

            if (xml.contains("Serie de TV")) {
                if (!movie.getMovieType().equals(Movie.TYPE_TVSHOW)) {
                    movie.setMovieType(Movie.TYPE_TVSHOW);
                    return false;
                }
            }

            if (!movie.isOverrideTitle()) {
                movie.setTitle(HTMLTools.extractTag(xml, "<img src=\"http://www.filmaffinity.com/images/movie.gif\" border=\"0\">", "</span></div></div>"));
            }

            if (StringTools.isNotValidString(movie.getPlot())) {
                String plot = HTMLTools.extractTag(xml, "SINOPSIS", 4, "><|", false);
                plot = StringTools.trimToLength(plot, preferredPlotLength, true, plotEnding);
                movie.setPlot(plot);
            }

        } catch (Exception error) {
            logger.severe("Failed retreiving filmaffinity data movie : " + movie.getId(IMDB_PLUGIN_ID));
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.severe(eResult.toString());
        }
        return true;
    }
}