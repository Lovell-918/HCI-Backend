package edu.nju.se.teamnamecannotbeempty.backend.data.converters;

import com.opencsv.bean.AbstractCsvConverter;
import edu.nju.se.teamnamecannotbeempty.backend.AppContextProvider;
import edu.nju.se.teamnamecannotbeempty.backend.dao.AffiliationDao;
import edu.nju.se.teamnamecannotbeempty.backend.po.Affiliation;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ToAffiliation extends AbstractCsvConverter {
    private final AffiliationDao affiliationDao;

    public ToAffiliation() {
        affiliationDao = AppContextProvider.getBean(AffiliationDao.class);
    }

    @Override
    public Object convertToRead(String value) {
        synchronized (affiliationDao) {
            Optional<Affiliation> result = affiliationDao.findByName(value);
            if (result.isPresent())
                return result.get();
            Affiliation affiliation = new Affiliation();
            affiliation.setName(value);
            return affiliationDao.saveAndFlush(affiliation);
        }
    }
}
