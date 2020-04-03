package edu.nju.se.teamnamecannotbeempty.backend.serviceImpl.visualization;

import edu.nju.se.teamnamecannotbeempty.backend.config.parameter.EntityMsg;
import edu.nju.se.teamnamecannotbeempty.backend.vo.GraphVO;
import edu.nju.se.teamnamecannotbeempty.backend.vo.Link;
import edu.nju.se.teamnamecannotbeempty.backend.vo.Node;
import edu.nju.se.teamnamecannotbeempty.data.domain.*;
import edu.nju.se.teamnamecannotbeempty.data.repository.AffiliationDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.AuthorDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.ConferenceDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.popularity.PaperPopDao;
import edu.nju.se.teamnamecannotbeempty.data.repository.popularity.TermPopDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompleteGraphFetch {

    private final AffiliationDao affiliationDao;
    private final AuthorDao authorDao;
    private final ConferenceDao conferenceDao;
    private final TermPopDao termPopDao;
    private final EntityMsg entityMsg;
    private final PaperPopDao paperPopDao;

    @Autowired
    public CompleteGraphFetch(AffiliationDao affiliationDao, AuthorDao authorDao, ConferenceDao conferenceDao, EntityMsg entityMsg,
                              TermPopDao termPopDao, PaperPopDao paperPopDao) {
        this.affiliationDao = affiliationDao;
        this.authorDao = authorDao;
        this.conferenceDao = conferenceDao;
        this.entityMsg = entityMsg;
        this.termPopDao = termPopDao;
        this.paperPopDao = paperPopDao;
    }

    @Cacheable(value = "getCompleteGraph", key = "#p0+'_'+#p1", unless = "#result=null")
    @Transactional
    public GraphVO getCompleteGraph(long id, int type) {
        if(type==entityMsg.getAuthorType()) return authorCompleteGraph(id);
        else if(type == entityMsg.getAffiliationType()) return affiliationCompleteGraph(id);
        else if(type == entityMsg.getConferenceType()) return conferenceCompleteGraph(id);
        else return null;
    }

    private GraphVO authorCompleteGraph(long id) {
        List<Paper.Popularity> paperPopList = paperPopDao.findTopPapersByAuthorId(id);
        List<Author> authorList = paperPopList.stream().flatMap(paperPop -> paperPop.getPaper().getAa().stream().map(
                Author_Affiliation::getAuthor)).collect(Collectors.toList());
        List<Node> nodes = generateAuthorNode(authorList);
        List<Link> links = paperPopList.stream().flatMap(paperPop -> paperPop.getPaper().getAa().stream()
                .filter(author_affiliation -> id != author_affiliation.getAuthor().getActual().getId()).map(
                        author_affiliation -> new Link(paperPop.getPaper().getId(), entityMsg.getPaperType(),
                                author_affiliation.getAuthor().getActual().getId(), entityMsg.getAuthorType(), -1)
                )).collect(Collectors.toList());

        List<Affiliation> affiliationList = affiliationDao.getAffiliationsByAuthor(id);
        List<Author> authorList1 = affiliationList.stream().flatMap(affiliation ->
                authorDao.getAuthorsByAffiliation(affiliation.getId()).stream()).collect(Collectors.toList());
        nodes.addAll(generateAuthorNode(authorList1));
        List<Link> links1 = affiliationList.stream().flatMap(affiliation ->
                authorDao.getAuthorsByAffiliation(affiliation.getActual().getId()).stream().filter(author -> id != author.getActual().getId())
                        .map(author -> new Link(affiliation.getActual().getId(), entityMsg.getAffiliationType(), author.getActual().getId(),
                                entityMsg.getAuthorType(), -1))).collect(Collectors.toList());
        links.addAll(links1);

        List<Term.Popularity> termPopList = termPopDao.getTermPopByAuthorID(id);
        List<Author> authorList2 = termPopList.stream().flatMap(
                termPop -> authorDao.getAuthorsByKeyword(termPop.getTerm().getId()).stream()
        ).collect(Collectors.toList());
        nodes.addAll(generateAuthorNode(authorList2));

        List<Link> links2 = termPopList.stream().flatMap(
                termPop->authorDao.getAuthorsByKeyword(termPop.getTerm().getId()).stream()
                        .filter(author -> id!=author.getActual().getId()).map(
                                author -> new Link(termPop.getTerm().getId(), entityMsg.getTermType(), author.getActual().getId(),
                                        entityMsg.getAuthorType(),paperPopDao.getWeightByAuthorOnKeyword(author.getActual().getId(),
                                        termPop.getTerm().getId()))
                        )).collect(Collectors.toList());
        links.addAll(links2);

        List<Node> nodeList = nodes.stream().distinct().filter(node -> id!=node.getEntityId()).collect(Collectors.toList());

        return new GraphVO(id, entityMsg.getAuthorType(), authorDao.findById(id).get().getName(),nodeList,links);
    }

    private GraphVO affiliationCompleteGraph(long id) {
        List<Term.Popularity> termPopList = termPopDao.getTermPopByAffiID(id);
        List<Link> links = termPopList.stream().flatMap(
                termPop -> affiliationDao.getAffiliationsByKeyword(termPop.getTerm().getId()).stream()
                        .filter(affiliation -> id!=affiliation.getActual().getId())
                        .map(affiliation -> new Link(termPop.getTerm().getId(), entityMsg.getTermType(),
                                affiliation.getActual().getId(), entityMsg.getAffiliationType(),
                                paperPopDao.getWeightByAffiOnKeyword(affiliation.getActual().getId(),termPop.getTerm().getId()))))
                .collect(Collectors.toList());
        List<Affiliation> affiliationList = termPopList.stream().flatMap(
                termPop -> affiliationDao.getAffiliationsByKeyword(termPop.getTerm().getId()).stream()
        ).collect(Collectors.toList());
        List<Node> preNodes = generateAffiliationNode(affiliationList);
        List<Node> nodes = preNodes.stream().filter(node -> id!=node.getEntityId()).distinct().collect(Collectors.toList());
        return new GraphVO(id,entityMsg.getAffiliationType(),affiliationDao.findById(id).get().getName(),nodes,links);
    }

    private GraphVO conferenceCompleteGraph(long id) {
        List<Paper.Popularity> paperPopList = paperPopDao.findTopPapersByConferenceId(id);
        List<Link> links = paperPopList.stream().flatMap(
                paperPop -> paperPop.getPaper().getAa().stream().map(
                        author_affiliation -> new Link(paperPop.getPaper().getId(), entityMsg.getPaperType(),
                                author_affiliation.getAuthor().getActual().getId(), entityMsg.getAuthorType(), -1)))
                .collect(Collectors.toList());

        List<Link> links1 = paperPopList.stream().flatMap(
                paperPop -> paperPop.getPaper().getAa().stream().map(
                        author_affiliation -> new Link(paperPop.getPaper().getId(), entityMsg.getPaperType(),
                                author_affiliation.getAffiliation().getActual().getId(), entityMsg.getAffiliationType(), -1)))
                .collect(Collectors.toList());
        links.addAll(links1);

        List<Link> links2 = paperPopList.stream().flatMap(
                paperPop -> paperPop.getPaper().getAa().stream().flatMap(
                        author_affiliation -> affiliationDao.getAffiliationsByAuthor(author_affiliation.getAuthor().getActual().getId())
                        .stream().filter(affiliation -> havePaperInTheConferenceByAffi(affiliation.getActual().getId(),id))
                                .map(affiliation -> new Link(author_affiliation.getAuthor().getActual().getId(),entityMsg.getAuthorType(),
                                        affiliation.getActual().getId(), entityMsg.getAffiliationType(), -1))
                )).collect(Collectors.toList());
        links.addAll(links2);

        List<Author_Affiliation> author_affiliationList = paperPopList.stream().flatMap(
                paperPop-> paperPop.getPaper().getAa().stream()).collect(Collectors.toList());
        List<Author> authorList = author_affiliationList.stream().map(Author_Affiliation::getAuthor).collect(Collectors.toList());
        List<Node> nodes = generateAuthorNode(authorList);
        List<Affiliation> affiliationList = author_affiliationList.stream().map(Author_Affiliation::getAffiliation).collect(Collectors.toList());
        nodes.addAll(generateAffiliationNode(affiliationList));

        List<Node> reNodes = nodes.stream().distinct().collect(Collectors.toList());
        List<Link> reLinks = links.stream().distinct().collect(Collectors.toList());

        return new GraphVO(id, entityMsg.getConferenceType(), conferenceDao.findById(id).get().buildName(), reNodes, reLinks);
    }

    private List<Node> generateAffiliationNode(List<Affiliation> affiliations) {
        return affiliations.stream().map(
                affiliation -> new Node(affiliation.getActual().getId(), affiliation.getName(), entityMsg.getAffiliationType()))
                .collect(Collectors.toList());
    }

    private List<Node> generateAuthorNode(List<Author> authors) {
        return authors.stream().map(
                author -> new Node(author.getActual().getId(), author.getName(), entityMsg.getAuthorType()))
                .collect(Collectors.toList());
    }


    private boolean havePaperInTheConferenceByAffi(long id, long conferenceId){
        List<Paper.Popularity> paperPops = paperPopDao.findTopPapersByConferenceId(id);
        for(Paper.Popularity paperPop:paperPops){
            if(paperPop.getPaper().getConference().getId() == id) return true;
        }
        return false;
    }
}
