/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.trans.agentFX;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.GCardStatus;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.replication.utility.LogWrapper;
import org.rmj.appdriver.agent.MsgBox;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCard;
import org.rmj.gcard.base.misc.GCEncoder;
import org.rmj.integsys.pojo.UnitGCard;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
public class XMGCardEncode {
    public XMGCardEncode(GRider foGRider, String fsBranchCd){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCard();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;

            poData = new UnitGCard();
            poTranDate = foGRider.getSysDate();
        }
    }

    public boolean searchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSearchSQL();
        if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same card number!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                             ((String) poData.getValue("sFrstName")) + " " +
                             ((String) poData.getValue("sMiddName"));
            if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                setMessage("The same client name!");
                return false;
            }
              
            lsSQL = MiscUtil.addCondition(lsSQL, "b.sCompnyNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
            
            //lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        if(loCon == null){
            setMessage("Invalid connection!");
            return false;
        }

        boolean lbHasRec = false;
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            System.out.println("Before Execute");

            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(lsSQL);

            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Name»Address»Activated»Card Number", 
                                                                "sClientNm»xAddressx»dTransact»a.sCardNmbr");
              
                if (loValue != null){
                    setMessage("GCard" + (String) loValue.get("sGCardNox"));
                    lbHasRec = loadTransaction((String) loValue.get("sGCardNox"));
                } else
                    setMessage("No record selected...");
            }

            System.out.println("After Execute");

        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }

        return lbHasRec;
    }

    public String getSearchSQL(){
        return "SELECT" +
                    "  a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = " + SQLUtil.toSQL(GCardStatus.PRINTED);
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
    }

    public int getEditMode(){
        return pnEditMode;
    }

    public void setTranDate(Date loDate){
        poTranDate = loDate;
    }

    public Date getTranDate(){
        return poTranDate;
    }

    public Object getMaster(String fsCol){
        return getMaster(poData.getColumn(fsCol));
    }

    public Object getMaster(int fnCol) {
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else{
            return poData.getValue(fnCol);
        }
    }

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getClientID());
        return poClntx;
    }

    public XMBranch getBranch(String sBranchCd){
        if(poBrnch == null)
            poBrnch = new XMBranch(poGRider, psBranchCd, true);

        poBrnch.openRecord(poData.getCompanyID());
        return poBrnch;
    }

    public XMGCApplication getApplication(String sBranchCd){
        if(poAppxx == null)
            poAppxx = new XMGCApplication(poGRider, psBranchCd, true);

        poAppxx.loadTransaction(poData.getApplicNo());
        return poAppxx;
    }

    public boolean loadTransaction(String fsTransNox){
        //Try to open the record
        poData = (UnitGCard) poCtrl.openRecord(fsTransNox);

        if(poData.getGCardNo() == null){
            //Inform that no record was opened
            pnEditMode = EditMode.UNKNOWN;
        }
        else{
            //set the values of foreign key(object) to null
            pnEditMode = EditMode.READY;

            if(poData.getDigital().equalsIgnoreCase("1")){
                poData = null;
                pnEditMode = EditMode.UNKNOWN;
                psMessage = "Digital GCard can't be encoded.";
                return false;
            }
        }

        poClntx = null;
        poAppxx = null;
        poBrnch = null;
        return (poData.getGCardNo() != null);
    }

    public boolean encode(){
        boolean lbCancel = false;
        boolean lbCardOk = false;
        String lsSerial;

        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");
            return false;      
        }
      
        logwrapr.info("XMGCardEncode.encode: Verifying PSC for " + poData.getCardNumber() + " using pin no " + psPin1 + psPin2);
        if(!GCEncoder.verifyPSC(psPin1, psPin2)){
            logwrapr.severe("XMGCardEncode.encode: Can't verify the pin number for GCard " + poData.getCardNumber() 
                            + " using Pin No " + psPin1 + psPin2 
                            + " » " + GCEncoder.getErrMessage());
            setMessage("Unable to verify the pin number!");
            return false;
        }

        String lsPin1 = poData.getPINumber().substring(0,2);
        String lsPin2 = poData.getPINumber().substring(2,4);
        XMClient loClt = getClient();

        GCEncoder.write(GCEncoder.BIRTH_DATE, loClt.getMaster("dBirthDte"));
        GCEncoder.write(GCEncoder.CARD_EXPIRY, poData.getExpiryDate());
        GCEncoder.write(GCEncoder.CARD_NUMBER, poData.getCardNumber());
        GCEncoder.write(GCEncoder.CARD_TYPE, poData.getCardType());
        GCEncoder.write(GCEncoder.CLIENT_ID, poData.getClientID());
        //TODO:get all unposted points here?
      
        if(poData.getAvailablePoints() == null){
           poData.setAvailablePoints(0.0);
        }
      
        GCEncoder.write(GCEncoder.POINTS, poData.getAvailablePoints().longValue());
        GCEncoder.write(GCEncoder.POINTS_EXPIRY, poData.getExpiryDate());

        //TODO:get all MCs involved in this transaction
        GCEncoder.write(GCEncoder.SERIAL1, "");
        GCEncoder.write(GCEncoder.SERIAL2, "");
        GCEncoder.write(GCEncoder.SERIAL3, "");
        //encoding of remaining mcs will be in writeserial
        //GCEncoder.write(GCEncoder.REMAINING_MC, (long) 0);
        writeSerial();

        GCEncoder.write(GCEncoder.TOWN_ID, loClt.getMaster("sTownIDxx"));
        GCEncoder.updatePSC(lsPin1, lsPin2);

        System.out.println("Birth Date:" + (Date) GCEncoder.read(GCEncoder.BIRTH_DATE));
        System.out.println("Card Expiry:" + (Date) GCEncoder.read(GCEncoder.CARD_EXPIRY));
        System.out.println("Card Number:" + (String) GCEncoder.read(GCEncoder.CARD_NUMBER));
        System.out.println("Card Type:" + (String) GCEncoder.read(GCEncoder.CARD_TYPE));
        System.out.println("Client ID:" + (String) GCEncoder.read(GCEncoder.CLIENT_ID));
        System.out.println("Points:" + (Long) GCEncoder.read(GCEncoder.POINTS));
        System.out.println("Points Expiry:" + (Date) GCEncoder.read(GCEncoder.POINTS_EXPIRY));
        System.out.println("Remaining MC:" + (Long) GCEncoder.read(GCEncoder.REMAINING_MC));
        System.out.println("Town ID:" + (String) GCEncoder.read(GCEncoder.TOWN_ID));
        System.out.println("Pin #1:" + String.valueOf(((String) GCEncoder.read(GCEncoder.RESERVED3)).getBytes()[0]));
        System.out.println("Pin #2:" + String.valueOf(((String) GCEncoder.read(GCEncoder.RESERVED5)).getBytes()[0]));

        //issue the encode method to record this process
        String stored = (String) GCEncoder.read(GCEncoder.CARD_NUMBER); 
      
        if(!poData.getCardNumber().equals(stored)){
            logwrapr.severe("encode: Stored Card Number is different from database " + stored);
            setMessage("Unable to encode data to card. Please inform SEG/SSG.");         
            return false;
        }

        poGRider.beginTrans();  
        lbCardOk = poCtrl.encode(poData.getGCardNo(),  poTranDate);

        //If method invoke returns false inform the user
        if(!lbCardOk){
            logwrapr.severe("encode: Can't save to database " + stored + ". " + poCtrl.getErrMsg() + poCtrl.getMessage());
            setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());
            poGRider.rollbackTrans();
            return false;
        }

        //Inform the user that the process was executed successfully
        setMessage("Data was encoded successfully!");

        poGRider.commitTrans();
        return true;
    }

    public boolean connectCard(){
        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");         
            return false;
        }

        if(!GCEncoder.init()){
            logwrapr.severe("connectCard: Can't Initialize card. " + GCEncoder.getErrMessage());
            setMessage("connectCard: Can't Initialize card. " + GCEncoder.getErrMessage());
            return false;
        }

        if(!GCEncoder.connect()){
            logwrapr.severe("connectCard: Can't Connect card. " + GCEncoder.getErrMessage());
            setMessage("connectCard: Can't Connect card. " + GCEncoder.getErrMessage());
            return false;
        }

        return true;
    }

    public boolean releaseCard(){
        if(!GCEncoder.disconnect()){
            setMessage(GCEncoder.getErrMessage());
            return false;
        }

        return loadTransaction("");
    }

    private void writeSerial(){
        XMGCApplication loData = new XMGCApplication(poGRider, psBranchCd, true);
        loData.loadTransaction(poData.getApplicNo());

        //if source is a motorcycle then create a service cuopon record for the motorcycle sold
        if(((String)loData.getMaster("sSourceNo")).equalsIgnoreCase("2910000005")){
            String lsSQL = "SELECT sSerialID" +
                            " FROM MC_SO_Detail" +
                            " WHERE sTransNox IN(" +
                                " SELECT sTransNox" +
                                " FROM MC_SO_Master" +
                                " WHERE sTransNox LIKE " + SQLUtil.toSQL(((String)loData.getMaster("sSourceNo")).substring(0, 2) + "%") +
                                  " AND sDRNoxxxx = " + SQLUtil.toSQL(((String)loData.getMaster("sSourceNo"))) +
                                  " AND dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(((Date)loData.getMaster("dTransact")), "yyyy-MM-dd")) + ")";
            Statement loStmtSerial = null;
            ResultSet loRSSerial = null;
            try {
                loStmtSerial = poGRider.getConnection().createStatement();
                loRSSerial = loStmtSerial.executeQuery(lsSQL);
                int lnCtr = 0;
                int lnSerialCtr = 0;
                while(loRSSerial.next()){
                    lnCtr++;
                    switch(lnCtr){
                       case 1:
                          GCEncoder.write(GCEncoder.SERIAL1, loRSSerial.getString("sSerialID"));
                          break;
                       case 2: 
                          GCEncoder.write(GCEncoder.SERIAL2, loRSSerial.getString("sSerialID"));
                          break;
                       case 3: 
                          GCEncoder.write(GCEncoder.SERIAL3, loRSSerial.getString("sSerialID"));
                          break;
                       default:
                          lnSerialCtr++;
                    }//switch(lnctr)
                }//while(loRSSerial.next())
                GCEncoder.write(GCEncoder.REMAINING_MC, (long) lnSerialCtr);

                setMessage("Application verified successfully!");
                pnEditMode = EditMode.UNKNOWN;

            }//try
            catch (SQLException ex) {
                Logger.getLogger(XMGCApplication.class.getName()).log(Level.SEVERE, null, ex);
                MsgBox.showOk(ex.getMessage());
                MiscUtil.close(loStmtSerial);
                MiscUtil.close(loRSSerial);
            }//catch-ex
        }//if-is mc
        else
            GCEncoder.write(GCEncoder.REMAINING_MC, (long) 0);
    }

    public void setBranch(String fsBranchCd) {
        psBranchCd = fsBranchCd;
        poCtrl.setBranch(psBranchCd);
    }

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}

    private UnitGCard poData;
    private GCard poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;

    private static LogWrapper logwrapr = new LogWrapper("XMGCardEncode", "temp/XMGCardEncode.log");
   
//   private String psPin1 = "60";
//   private String psPin2 = "23";

    private String psPin1 = "255";
    private String psPin2 = "255";

    private XMClient poClntx = null;
    private XMBranch poBrnch = null;
    private XMGCApplication poAppxx = null;

    private Date poTranDate = null;
}
