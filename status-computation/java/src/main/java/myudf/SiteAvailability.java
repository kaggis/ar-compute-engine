package myudf;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pig.EvalFunc;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import utils.ExternalResources;
import utils.State;
import utils.Utils;

/**
 *
 * @author Anastasis Andronidis <anastasis90@yahoo.gr>
 */
public class SiteAvailability extends EvalFunc<Tuple> {
    
    private final double quantum = 288.0;
    private final TupleFactory mTupleFactory = TupleFactory.getInstance();
    private Map<String, String> weights = null;
    
    private Integer nGroups = null;
    private Map<String, Map<String, Integer>> hlps = null;

    @Override
    public Tuple exec(Tuple tuple) throws IOException {
        State[] output_table = null;
        Map<Integer, State[]> ultimate_kickass_table = null;
        
        if (this.weights == null) {
            this.weights = ExternalResources.initWeights((String) tuple.get(2));
        }

        if (this.hlps == null) {
            this.hlps = ExternalResources.initHLPs("83.212.110.19", 27017);
        }
        
        Map<String, Integer> highLevelProfiles = this.hlps.get((String) tuple.get(1));
        if (highLevelProfiles == null) {
            return null;
        }
        this.nGroups = Collections.max(highLevelProfiles.values());
        
        String service_flavor;
        State[] timeline = new State[(int)this.quantum];
        
        ultimate_kickass_table = new HashMap<Integer, State[]>();
        
        for (Tuple t : (DataBag) tuple.get(0)) {            
            service_flavor = (String) t.get(4);
            String [] tmpa = ((String) t.get(2)).substring(1, ((String)t.get(2)).length() - 1).split(", ");
            
            for (int i = 0; i<tmpa.length; i++) {
                timeline[i] = State.valueOf(tmpa[i]);
            }

            // Future: serialize objects
//            try {
//                byte[] data = javax.xml.bind.DatatypeConverter.parseBase64Binary((String) t.get(3));
//                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
//                timeline = (State[]) ois.readObject();
//                ois.close();
//            } catch (Exception ex) {
//                Logger.getLogger(SiteAvailability.class.getName()).log(Level.SEVERE, null, ex);
//            }

            Integer group_id = highLevelProfiles.get(service_flavor);
            
            if (ultimate_kickass_table.containsKey(group_id)) {
                Utils.makeOR(timeline, ultimate_kickass_table.get(group_id));
            } else {
                if (group_id!=null) {
                    ultimate_kickass_table.put(group_id, timeline);
                } 
//                else {
//                    String msg = "Encounterd: " + service_flavor;
//                    Logger.getLogger(SiteAvailability.class.getName()).log(Level.INFO, msg);
//                }
            }
        }
        
        // We get the first table, we dont care about the first iteration
        // because we do an AND with self.
        if (ultimate_kickass_table.size() > this.nGroups) {
            // this.output_table = new String[24];
            // Utils.makeMiss(this.output_table);
            throw new UnsupportedOperationException("A site has more flavors than expected. Something is terribly wrong! " + ultimate_kickass_table.keySet());
        } else {
            if (ultimate_kickass_table.values().size() > 0) {
                output_table = ultimate_kickass_table.values().iterator().next();
                for (State[] tb : ultimate_kickass_table.values()) {
                    Utils.makeAND(tb, output_table);
                }
            } else {
                output_table = new State[(int)this.quantum];
                Utils.makeMiss(output_table);
            }
        }
        
        String w = this.weights.get((String) tuple.get(3));
        if (w == null) {
            w = "1";
        }
        
        Tuple t = Utils.getARReport(output_table, mTupleFactory.newTuple(6), this.quantum);
        t.set(5, w);
        return t;
    }
    
    @Override
    public Schema outputSchema(Schema input) {
        try {
            Schema.FieldSchema availA   = new Schema.FieldSchema("availability", DataType.DOUBLE);
            Schema.FieldSchema availR   = new Schema.FieldSchema("reliability",  DataType.DOUBLE);
            Schema.FieldSchema up       = new Schema.FieldSchema("up",           DataType.DOUBLE);
            Schema.FieldSchema unknown  = new Schema.FieldSchema("unknown",      DataType.DOUBLE);
            Schema.FieldSchema down     = new Schema.FieldSchema("downtime",     DataType.DOUBLE);
            Schema.FieldSchema weight   = new Schema.FieldSchema("weight",       DataType.CHARARRAY);

            Schema p_metricS = new Schema();
            p_metricS.add(availA);
            p_metricS.add(availR);
            p_metricS.add(up);
            p_metricS.add(unknown);
            p_metricS.add(down);
            p_metricS.add(weight);
            
            return new Schema(new Schema.FieldSchema("Availability_Report", p_metricS, DataType.TUPLE));
        } catch (FrontendException ex) {
            Logger.getLogger(SiteAvailability.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

}