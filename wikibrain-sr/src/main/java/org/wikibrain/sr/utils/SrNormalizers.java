package org.wikibrain.sr.utils;

import gnu.trove.set.TIntSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.lang.LocalString;
import org.wikibrain.core.model.UniversalPage;
import org.wikibrain.sr.MonolingualSRMetric;
import org.wikibrain.sr.SRResult;
import org.wikibrain.sr.SRResultList;
import org.wikibrain.sr.UniversalSRMetric;
import org.wikibrain.sr.dataset.Dataset;
import org.wikibrain.sr.disambig.Disambiguator;
import org.wikibrain.sr.normalize.IdentityNormalizer;
import org.wikibrain.sr.normalize.Normalizer;
import org.wikibrain.utils.ParallelForEach;
import org.wikibrain.utils.Procedure;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pair of normalizers (similarity, mostSimilar) persisted to disk.
 *
 * @author Shilad Sen
 */
public class SrNormalizers {
    private static final Logger LOG = Logger.getLogger(SrNormalizers.class.getName());

    public static final String SIMILARITY_NORMALIZER = "similarityNormalizer";
    public static final String MOST_SIMILAR_NORMALIZER = "mostSimilarNormalizer";

    private Normalizer mostSimilarNormalizer = new IdentityNormalizer();
    private Normalizer similarityNormalizer = new IdentityNormalizer();

    public SrNormalizers() {}

    public Normalizer getMostSimilarNormalizer() {
        return mostSimilarNormalizer;
    }

    public Normalizer getSimilarityNormalizer() {
        return similarityNormalizer;
    }

    public void setMostSimilarNormalizer(Normalizer normalizer) {
        this.mostSimilarNormalizer = normalizer;
    }

    public void setSimilarityNormalizer(Normalizer normalizer) {
        this.similarityNormalizer = normalizer;
    }

    public void clear(File dir) {
        FileUtils.deleteQuietly(new File(dir, MOST_SIMILAR_NORMALIZER));
        FileUtils.deleteQuietly(new File(dir, SIMILARITY_NORMALIZER));
    }

    public boolean hasReadableNormalizers(File dir) {
        return isValidNormalizer(dir, MOST_SIMILAR_NORMALIZER) && isValidNormalizer(dir, SIMILARITY_NORMALIZER);
    }

    /**
     * Reads the noramlizers from disk.
     * This method expects the files exist and are valid, so hasNormalizers should be called first.
     * @throws java.io.IOException
     */
    public void read(File dir) throws IOException {
        mostSimilarNormalizer = readNormalizer(dir, MOST_SIMILAR_NORMALIZER);
        similarityNormalizer = readNormalizer(dir, SIMILARITY_NORMALIZER);
    }

    public void write(File dir) throws IOException {
        writeNormalizer(dir, MOST_SIMILAR_NORMALIZER, mostSimilarNormalizer);
        writeNormalizer(dir, SIMILARITY_NORMALIZER, similarityNormalizer);
    }

    /**
     * Returns true if a normalizer exists and it is trained.
     * @param name
     * @return
     */
    private boolean isValidNormalizer(File dir, String name) {
        File path = new File(dir, name);
        if (!path.isFile()) {
            return false;
        }
        try {
            return readNormalizer(dir, name).isTrained();
        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "Failed to load normalizer at " + path.getAbsolutePath() +
                    ". Setting it to be invalid. Traceback:", e);
            return false;
        }
    }

    /**
     *
     * @param metric
     * @param dataset
     */
    public void trainSimilarity(final MonolingualSRMetric metric, Dataset dataset) {
        if (similarityNormalizer instanceof  IdentityNormalizer) {
            return;
        }
        if (!dataset.getLanguage().equals(metric.getLanguage())) {
            throw new IllegalArgumentException("SR metric has language " + metric.getLanguage() + " but dataset has language " + dataset.getLanguage());
        }
        final Normalizer trainee = similarityNormalizer;
        similarityNormalizer = new IdentityNormalizer();
        try {
            trainee.reset();
            ParallelForEach.loop(dataset.getData(), new Procedure<KnownSim>() {
                public void call(KnownSim ks) throws IOException, DaoException {
                    ks.maybeSwap();
                    SRResult sim = metric.similarity(ks.phrase1, ks.phrase2, false);
                    trainee.observe(sim == null ? Double.NaN : sim.getScore(), ks.similarity);
                }
            }, 100);
            trainee.observationsFinished();
            LOG.info("trained similarity normalizer: " + trainee.dump());
        } finally {
            similarityNormalizer = trainee;
        }
    }

    /**
     *
     * @param metric
     * @param dataset
     */
    public void trainSimilarity(final UniversalSRMetric metric, Dataset dataset) {
        if (similarityNormalizer instanceof  IdentityNormalizer) {
            return;
        }
        final Normalizer trainee = similarityNormalizer;
        similarityNormalizer = new IdentityNormalizer();
        try {
            trainee.reset();
            ParallelForEach.loop(dataset.getData(), new Procedure<KnownSim>() {
                public void call(KnownSim ks) throws IOException, DaoException {
                    ks.maybeSwap();
                    LocalString ls1 = new LocalString(ks.language,ks.phrase1);
                    LocalString ls2 = new LocalString(ks.language,ks.phrase2);
                    SRResult sim = metric.similarity(ls1, ls2, false);
                    trainee.observe(sim.getScore(), ks.similarity);
                }
            }, 100);
            trainee.observationsFinished();
            LOG.info("trained similarity normalizer: " + trainee.dump());
        } finally {
            similarityNormalizer = trainee;
        }
    }


    /**
     *
     * @param metric
     * @param disambiguator
     * @param dataset
     * @param validIds
     * @param maxResults
     */
    public void trainMostSimilar(final MonolingualSRMetric metric, final Disambiguator disambiguator, Dataset dataset, final TIntSet validIds, final int maxResults) {
        if (similarityNormalizer instanceof  IdentityNormalizer) {
            return;
        }
        if (!dataset.getLanguage().equals(metric.getLanguage())) {
            throw new IllegalArgumentException("SR metric has language " + metric.getLanguage() + " but dataset has language " + dataset.getLanguage());
        }

        final Normalizer trainee = mostSimilarNormalizer;
        mostSimilarNormalizer = new IdentityNormalizer();
        try {
            trainee.reset();
            ParallelForEach.loop(dataset.getData(), new Procedure<KnownSim>() {
                public void call(KnownSim ks) throws IOException, DaoException {
                    ks.maybeSwap();
                    List<LocalString> localStrings = new ArrayList<LocalString>();
                    localStrings.add(new LocalString(ks.language, ks.phrase1));
                    localStrings.add(new LocalString(ks.language, ks.phrase2));
                    List<LocalId> ids = disambiguator.disambiguateTop(localStrings, null);
                    if (ids != null && ids.size() == 2 && ids.get(0) != null && ids.get(1) != null) {
                        LocalId lid1 = ids.get(0);
                        LocalId lid2 = ids.get(1);
                        SRResultList dsl = metric.mostSimilar(lid1.getId(), maxResults, validIds);
                        if (dsl != null) {
                            trainee.observe(dsl, dsl.getIndexForId(lid2.getId()), ks.similarity);
                        }
                    }
                }
            }, 100);
            trainee.observationsFinished();
            LOG.info("trained most similar normalizer: " + trainee.dump());
        } finally {
            mostSimilarNormalizer = trainee;
        }
    }

    /**
     *
     * @param metric
     * @param disambiguator
     * @param dataset
     * @param validIds
     * @param maxResults
     */
    public void trainMostSimilar(final UniversalSRMetric metric, final Disambiguator disambiguator, final UniversalPageDao dao, final int algorithmId, Dataset dataset, final TIntSet validIds, final int maxResults) {
        if (similarityNormalizer instanceof  IdentityNormalizer) {
            return;
        }
        final Normalizer trainee = mostSimilarNormalizer;
        mostSimilarNormalizer = new IdentityNormalizer();
        try {
            trainee.reset();
            ParallelForEach.loop(dataset.getData(), new Procedure<KnownSim>() {
                public void call(KnownSim ks) throws IOException, DaoException {
                    ks.maybeSwap();
                    List<LocalString> localStrings = new ArrayList<LocalString>();
                    localStrings.add(new LocalString(ks.language, ks.phrase1));
                    localStrings.add(new LocalString(ks.language, ks.phrase2));
                    List<LocalId> ids = disambiguator.disambiguateTop(localStrings, null);
                    if (ids != null && ids.size() == 2) {
                        int pageId1 = dao.getUnivPageId(ids.get(0).asLocalPage());
                        int pageId2 = dao.getUnivPageId(ids.get(1).asLocalPage());
                        UniversalPage page = dao.getById(pageId1);
                        if (page != null) {
                            SRResultList dsl = metric.mostSimilar(page, maxResults, validIds);
                            if (dsl != null) {
                                trainee.observe(dsl, dsl.getIndexForId(pageId2), ks.similarity);
                            }
                        }
                    }
                }
            }, 100);
            trainee.observationsFinished();
            LOG.info("trained most similar normalizer: " + trainee.dump());
        } finally {
            mostSimilarNormalizer = trainee;
        }
    }


    /**
     * Reads a single normalizer from disk.
     * @param name
     * @return
     * @throws java.io.IOException
     */
    private Normalizer readNormalizer(File dir, String name) throws IOException {
        ObjectInputStream oip = null;
        try {
            oip = new ObjectInputStream(new FileInputStream(new File(dir, name)));
            return (Normalizer)oip.readObject();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);  // should not happen
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);  // should not happen
        } finally {
            if (oip != null) IOUtils.closeQuietly(oip);
        }
    }

    /**
     * Writes a single normalizer to disk.
     * @param dir
     * @param name
     * @param normalizer
     * @throws IOException
     */
    private void writeNormalizer(File dir, String name, Normalizer normalizer) throws IOException {
        ObjectOutputStream oop = new ObjectOutputStream(new FileOutputStream(new File(dir, name)));
        oop.writeObject(normalizer);
        oop.flush();
        oop.close();
    }
}
