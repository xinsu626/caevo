package timesieve;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Iterator;


/**
 * Class to compute closure over a set of temporal relations.
 * @author Nate Chambers
 */
public class Closure {
  static boolean report = true;
  static String rulePath = "closure.dat";
  HashMap<String,TLink.TYPE> rules[];

  public Closure() { 
    this(rulePath);
  }

  public Closure(String path) { 
    loadClosureRules(path);
  }

  // 0: A-B A-C
  // 1: A-B C-A
  // 2: B-A A-C
  // 3: B-A C-A
  private TLink.TYPE closeLinks(TLink.TYPE relation1, TLink.TYPE relation2, int matchCase) {
    return rules[matchCase].get(relation1 + " " + relation2);
  }

  /**
   * Reads the closure rules from a data file
   */
  private void loadClosureRules(String path) {
    try {
      int matchCase = 0;
      int numAdded = 0;
      BufferedReader in = new BufferedReader(new FileReader(path));
      System.out.println("Loading closure rules from " + path);

      rules = new HashMap[4];
      for( int i = 0; i < 4; i++ ) {
        rules[i] = new HashMap<String,TLink.TYPE>();
      }

      while( in.ready() ) {
        String line = in.readLine();

        // defines start of new match case
        if( line.matches(".*A-B A-C.*") ) matchCase = 0;
        else if( line.matches(".*A-B C-A.*") ) matchCase = 1;
        else if( line.matches(".*B-A A-C.*") ) matchCase = 2;
        else if( line.matches(".*B-A C-A.*") ) matchCase = 3;

        // line comments
        else if( line.indexOf('/') > -1 ) { }
        // e.g. "TLink.SIMULTANEOUS TLink.ENDS TLink.ENDS"
        else if( line.length() > 5 ) {
          String parts[] = line.split("\\s+");
          TLink.TYPE first  = TLink.TYPE.valueOf(parts[0]);
          TLink.TYPE second = TLink.TYPE.valueOf(parts[1]);
          TLink.TYPE closed = TLink.TYPE.valueOf(parts[2]);

//          System.out.println("Adding closure rule: " + first + " " + second + " " + closed);
          rules[matchCase].put(first + " " + second, closed);
          numAdded++;
        }
      }

      // Sanity check.
      if( numAdded == 0 ) {
        System.out.println("WARNING!! Didn't load any closure rules. Was I supposed to?");
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Returns true if the given link is consistent with the rest of the
   * relations.  This function actually performs closure, so the test is
   * pretty expensive to run.
   * @return True if the link is consistent, false otherwise
   */    
  public boolean isConsistent(Collection<TLink> relations, TLink link) {
    // Hash what we've seen already
    HashMap<String,TLink.TYPE> seen = new HashMap<String,TLink.TYPE>();
    for( TLink tlink : relations )
      seen.put(tlink.event1()+tlink.event2(), tlink.relation());
    
    int status = newLinkStatus(seen, link.event1(), link.event2(), link.relation());

    if( status == 2 ) return false;
    else return true;
  }

  /**
   * Compute a larger closed set of relations using transitivity rules of 
   * temporal reasoning.  Adds the new links directly to the relations Vector.
   * @param relations A Vector of TLinks
   * @return True if the closure is consistent, false if a conflict occurred.
   */
  public boolean computeClosure(List<TLink> relations, List<TLink> newRelations, boolean prints) {
    boolean noneAdded = false;
    TLink tlink1 = null, tlink2 = null;
    String eid1, eid2, B, C;
    int matchCase = 0;
    int size = 0, oldsize;
    int start;
    boolean conflict = false;

    if( prints ) report = true;
    else report = false;

    if( report ) {
      System.out.println("Computing Closure (" + relations.size() + " relations)");
    }

    // Save what we've seen already
    HashMap<String,TLink.TYPE> seen = new HashMap<String,TLink.TYPE>();
    for( TLink tlink : relations )
      seen.put(tlink.event1()+tlink.event2(), tlink.relation());

    while (!noneAdded) {
      //System.out.println("iter = " + iter);
      oldsize = size;
      size = relations.size();
      for (int i = 0; i < size; i++) {
        if (i >= oldsize) start = i + 1;
        else start = oldsize;
        tlink1 = relations.get(i);
        TLink.TYPE rel1 = tlink1.relation();
        eid1 = tlink1.event1();
        eid2 = tlink1.event2();

        for (int j = start; j < size; j++) {
          tlink2 = relations.get(j);
          TLink.TYPE rel2 = tlink2.relation();
          B = null;
          C = null;

          // Find which out of 4 transitive patterns to use

          // A-B-Rel, A-C-Rel
          if( eid1.equals(tlink2.event1()) && !eid2.equals(tlink2.event2()) ) {
            matchCase = 0;
            B = eid2;
            C = tlink2.event2();
          }
          // A-B-Rel, C-A-Rel
          else if( eid1.equals(tlink2.event2()) && !eid2.equals(tlink2.event1()) ) {
            matchCase = 1;
            B = eid2;
            C = tlink2.event1();
          } 
          // B-A-Rel, A-C-Rel
          else if( eid2.equals(tlink2.event1()) && !eid1.equals(tlink2.event2()) ) {
            matchCase = 2;
            B = eid1;
            C = tlink2.event2();
          } 
          //B-A-Rel, C-A-Rel
          else if( eid2.equals(tlink2.event2()) && !eid1.equals(tlink2.event1()) ) {
            matchCase = 3;
            B = eid1;
            C = tlink2.event1();
          }

          // Ignore closing trivial relations such as A-A-INCL, A-A-SIMUL
          if( eid1.equals(eid2) &&
              (rel1 == TLink.TYPE.SIMULTANEOUS || rel1 == TLink.TYPE.INCLUDES) ) {
            matchCase = -1;
          } 
          else if( tlink2.event2().equals(tlink2.event1()) &&
              (rel2 == TLink.TYPE.SIMULTANEOUS || rel2 == TLink.TYPE.INCLUDES ) ) {
            matchCase = -1;
          }

          if( B != null && C != null && matchCase != -1 ) {
//            System.out.println("Checking B=" + B + " C=" + C + " case=" + matchCase);
            // Find the relation to close it	  
            TLink.TYPE newrel = closeLinks(rel1, rel2, matchCase);
//            System.out.println(rel1 + " " + rel2 + " newrel=" + newrel);
            if( newrel != null ) {
//              System.out.println("New link! " + newrel);
              TLink newLink = addlink(seen, relations, B, C, newrel);
              // If this new link conflicts, remember that
              if( newLink == null ) conflict = true;
              else newRelations.add(newLink);
            }
          }
        }
      }
      noneAdded = (relations.size() == size);
    }

    return conflict;
  }

  /**
   * Computes closure over the given relations, and returns a list of new links created by the closure rules.
   * @param relations List of known relations.
   * @return
   */
  public List<TLink> computeClosure(List<TLink> relations) {
    return computeClosure(relations, false);
  }

  public List<TLink> computeClosure(List<TLink> relations, boolean debug) {
    List<TLink> cloned = new ArrayList<TLink>(relations);
    List<TLink> newRelations = new ArrayList<TLink>();
    computeClosure(cloned, newRelations, debug);
    return newRelations;
  }    

  private TLink.TYPE getClosed(int matchCase, TLink.TYPE relation, TLink.TYPE relation2) {
    //    System.out.println("closing..." + relation + " " + relation2);
    TLink.TYPE rel = closeLinks(relation, relation2, matchCase);
    //    System.out.println("closed..." + rel);
    return rel;
  }

  /**
   * Creates the appropriate type of TLink, based on the string from of A and B.
   * If it is e30 or ei12 then it is an event, whereas t14 is a time.
   * @param relations A vector of TLinks to which to add a new link
   * @param A The id of the first event/time
   * @param B The id of the second event/time
   * @param relation The type of relation between A and B
   * @return The new link that was added to the relations list. null if the link was duplicate, or conflicted.
   */
  private TLink addlink(HashMap<String,TLink.TYPE> seen, List<TLink> relations, String A, String B, TLink.TYPE rel) {
    int status = newLinkStatus(seen, A, B, rel);
    
    if( status == 0 ) {
      TLink link;
      int times = 0;

      // See what type of relation we are adding (e.g. event-time)
      // YES, this depends on making sure all time variables start with 't'
      if( A.charAt(0) == 't' ) times++;
      if( B.charAt(0) == 't' ) times++;

      // Create the appropriate TLink
      if( times == 2 ) link = new TimeTimeLink(A, B, rel, true);
      else if( times == 1 ) link = new EventTimeLink(A, B, rel, true);
      else link = new EventEventLink(A, B, rel, true);

      relations.add(link);
      seen.put(A+B,rel);

      //      System.out.println("Added link " + A + " " + relation + " " + B);
      return link;
    }
    else return null;
  }
  
  /**
   * Tells you if a new proposed link A-B is ok with the current relations.
   * 0: doesn't exist.
   * 1: already exists, or is consistent with existing relation A-B
   * 2: conflicts with existing relation between A-B
   */
  private int newLinkStatus(HashMap<String,TLink.TYPE> seen, String A, String B, TLink.TYPE rel) {
    // Make sure we don't already have a relation
    if( seen.containsKey(A+B) ) {
      TLink.TYPE current = seen.get(A+B);
      if( current != rel ) {

        // some relation clashes are ok
        if( (current == TLink.TYPE.BEFORE  && rel == TLink.TYPE.IBEFORE) ||
            (current == TLink.TYPE.IBEFORE && rel == TLink.TYPE.BEFORE) )
          return 1;

        if( report ) {
          System.err.println("Closure conflict: " + A + " " + B);
          System.err.println("...old relation " + A + " " + seen.get(A+B) + " " + B + " adding new relation " + A + " " + rel + " " + B);
        }
        return 2;
      } 
      else return 1; // exact same relation already exists
    }
    // Make sure the inverse relation doesn't exist
    else if( seen.containsKey(B+A) ) {
      TLink.TYPE reverse = seen.get(B+A);
      TLink.TYPE relReversed = TLink.invertRelation(rel);
      // inverse simultaneous relations are harmless, just ignore
      if( reverse == relReversed ||
          // INCLUDES and BEGINS/ENDS is ok
          (rel == TLink.TYPE.INCLUDES && (reverse == TLink.TYPE.BEGINS || reverse == TLink.TYPE.ENDS)) ||
          // BEGINS/ENDS and INCLUDES is ok
          (reverse == TLink.TYPE.INCLUDES && (rel == TLink.TYPE.BEGINS || rel == TLink.TYPE.ENDS)) 
          )
        return 1;

      if( report ) {
        System.err.println("Closure conflict: " + A + " " + B);
        System.err.println("...old relation " + B + " " + seen.get(B+A) + " " + A + " adding new relation " + A + " " + rel + " " + B);
      }
      return 2;
    }
    // Else, you can add the new relation.
    else return 0;
  }
  
  
  /**
   * Generates a complete set of NONE tlinks between all pairs of events that
   * are not already tlinks.  One pair A-B or B-A, not both A-B and B-A.
   * @param tlinks The current TLinks in the document.
   * @param events All of the events in the document.
   * @param eiidToID A mapping from the eiid's to id's in TimeBank. Use null if no mapping needed (TempEval).
   * @return Vector of NONE tlinks
   */
  public Vector<TLink> addNoneLinks(List<TLink> tlinks, List<TextEvent> events, Map<String,String> eiidToID) {
    Vector<TLink> newlinks = new Vector<TLink>();
    HashMap<String,HashSet<String>> map = new HashMap<String,HashSet<String>>();

    // Save the tlinks for quick access
    for( TLink link : tlinks ) {
      HashSet<String> set = map.get(link.event1());
      if( set == null ) set = new HashSet<String>();
      set.add(link.event2());
      map.put(link.event1(), set);
    }

    // Generate all pairs of NONE links
    for( TextEvent event1 : events ) {
      for( TextEvent event2 : events ) {
        if( !event1.equals(event2) ) {
          // only event pairs 1 sentence away
          if( Math.abs(event1.sid() - event2.sid()) < 2 ) {
            String id1 = event1.eiid();
            String id2 = event2.eiid();
            if( eiidToID != null && eiidToID.containsKey(id1) ) id1 = eiidToID.get(id1);
            if( eiidToID != null && eiidToID.containsKey(id2) ) id2 = eiidToID.get(id2);

            // if this pair is not yet linked
            if( !containsLink(map, id1, id2) ) {
              // randomly choose order
              String first = id1;
              String second = id2;
              if( Math.random() < 0.5 ) {
                first = second;
                second = event1.eiid();
              }

              // Add to the map
              HashSet<String> set = map.get(first);
              if( set == null ) set = new HashSet<String>();
              set.add(second);
              map.put(first, set);

              // Create TLink
              newlinks.add(new EventEventLink(first, second, "none"));
            }
          }
        }
      }
    }
    return newlinks;
  }


  /**
   * @return True if the relation event1-event2 or event2-event1 exists in the hashmap
   */
  private boolean containsLink(HashMap<String,HashSet<String>> map, String event1, String event2) {
    if( map.containsKey(event1) && map.get(event1).contains(event2) )
      return true;
    else if( map.containsKey(event2) && map.get(event2).contains(event1) )
      return true;
    else return false;
  }

  public void printRules() {
    for( int i = 0; i < rules.length; i++ ) {
      System.out.println("i=" + i);
      System.out.println(rules[i]);
    }
  }
}
