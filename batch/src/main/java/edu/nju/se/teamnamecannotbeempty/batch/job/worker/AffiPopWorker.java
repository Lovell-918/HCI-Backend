package edu.nju.se.teamnamecannotbeempty.batch.job.worker;

import edu.nju.se.teamnamecannotbeempty.data.domain.Affiliation;
import edu.nju.se.teamnamecannotbeempty.data.repository.AffiliationDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.popularity.AffiPopDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.popularity.PaperPopDao;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class AffiPopWorker {
    private final AffiliationDao affiliationDao;
    private final AffiPopDao affiPopDao;
    private final PaperPopDao paperPopDao;

    @Autowired
    public AffiPopWorker(AffiliationDao affiliationDao, AffiPopDao affiPopDao, PaperPopDao paperPopDao) {
        this.affiliationDao = affiliationDao;
        this.affiPopDao = affiPopDao;
        this.paperPopDao = paperPopDao;
    }

    @Async
    public Future<?> generateAffiPop() {
        affiPopDao.saveAll(affiliationDao.getAll().stream().map(this::generatePop).collect(Collectors.toList()));
        LoggerFactory.getLogger(getClass()).info("Done generate affiliation popularity");
        return new AsyncResult<>(null);
    }

    Affiliation.Popularity generatePop(Affiliation affi) {
        Affiliation actual = affi.getActual();
        Optional<Affiliation.Popularity> result = affiPopDao.findByAffiliation_Id(actual.getId());
        Affiliation.Popularity pop = result.orElse(new Affiliation.Popularity(actual, 0.0));
        if (!actual.getName().equals("NA")) {
            Double sum = paperPopDao.getPopSumByAffiId(actual.getId());
            sum = sum == null ? 0.0 : sum;
            pop.setPopularity(pop.getPopularity() + sum);
        }
        return pop;
    }
}
