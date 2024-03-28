/*
 * To change this template, choose Tools | Templates
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
import org.rmj.appdriver.constants.RecordStatus;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.replication.utility.LogWrapper;
import org.rmj.appdriver.agent.MsgBox;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.agent.misc.XMGCPointBasis;
import org.rmj.gcard.formFX.ServiceCoupon;
import org.rmj.gcard.service.GCRestAPI;
import org.rmj.gcard.trans.GCApplication;
import org.rmj.integsys.pojo.UnitGCApplication;
import org.rmj.parameters.agent.XMBranch;
import org.rmj.parameters.agent.XMMCModel;
import org.rmj.webcamfx.ui.Webcam;

/**
 *
 * @author kalyptus
 */
public class XMGCApplication{
    public XMGCApplication(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCApplication();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;

            poData = new UnitGCApplication();
        }
    }

    public void setMaster(String fsCol, Object foData){
        setMaster(poData.getColumn(fsCol), foData);
    }

    public void setMaster(int fnCol, Object foData) {
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
        // Don't allow update for sTransNox, cTranStat, sModified, and dModified
            if(!(fnCol == poData.getColumn("sTransNox") ||
                fnCol == poData.getColumn("cTranStat") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){

                //if field is dTransact - a Date
                if(fnCol == poData.getColumn("dTransact")){
                    if(foData instanceof Date)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, null);
                }
                //if field is nAmtPaidx - a numeric
                else if(fnCol == poData.getColumn("nAmtPaidx")){
                    if(foData instanceof Number)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, 0.00);
                }
                //all other fields - string type
                else{
                    poData.setValue(fnCol, foData);
                }
            }
        }
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

    public boolean newTransaction() {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCApplication) poCtrl.newTransaction();

        if(poData == null){
            setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
            return false;
        } else{
            poData.setCompanyID(psBranchCd);
            poData.setTransactDate(poGRider.getSysDate());
            //set the values of foreign key(object) to null
            poClntx = null;

            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean loadTransaction(String fsTransNox) {
        if(poCtrl == null){
            pnEditMode = EditMode.UNKNOWN;
            return false;
        }

        poData = (UnitGCApplication) poCtrl.loadTransaction(fsTransNox);

        if(poData.getTransNo() == null){
            if(!fsTransNox.equalsIgnoreCase("x")) 
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
            
            pnEditMode = EditMode.UNKNOWN;
            return false;
        }//if(poData.getTransNo() == null)
        else{
            //set the values of foreign key(object) to null
            poClntx = null;

            //auto-load serial for previously created gcard applications
            if(poData.getSource().equalsIgnoreCase("M02910000005")){
                //since entry of fsec/engine is not yet implemented in 
                //the branch area, we need to perform the following test to
                //prevent runtime error to occur
                //By: kalyptus - 2012.03.02
                //+++++++++++++++++++++++++++++++++++++++
                //white?
                if(!(poData.getWhite() instanceof Number))
                    poData.setWhite(0);
            
                //yellow?   
                if(!(poData.getYellow() instanceof Number))
                    poData.setYellow(0);
            
                //points?
                if(!(poData.getPoints() instanceof Number))
                    poData.setPoints(0.00);

                //serial?
                if(!(poData.getSerialID() instanceof String))
                    poData.setSerialID("");
            
                if(poData.getSerialID().equals("")){
                    poData.setSerialID(autoSerialID(poData.getTransNo().substring(0, 4), poData.getSourceNo(), poData.getTransactDate()));
                    //verify if serial is loaded successfully...   
                    if(poData.getSerialID().equals("")){
                        setMessage("Unable to load the SERIAL ID!");
                        return false; //added
                    }
                    //testing is upt to this area....   
                    //+++++++++++++++++++++++++++++++++++++++
                }
            }
         
            pnEditMode = EditMode.READY;
            return true;
        }//if(poData.getTransNo() == null) - else
    }
    
    public boolean displayGCardApp(){
        if (poData == null){
            setMessage("Master data is null...");
            return false;
        }
        
        if (pnEditMode != EditMode.READY) {
            setMessage("Invalid edit mode detected...");
            return false;
        }
        
        if (!poData.getDigital().equals("1")){
            return true;
        }
        
        JSONObject loResult = GCRestAPI.ApproveApplication(poGRider, poData, (String) poClntx.getMaster("sMobileNo"));
        
        if (loResult.get("result").toString().equalsIgnoreCase("success")){
            String lsCardNmbr = (String) loResult.get("sCardNmbr");
            lsCardNmbr = "APPLICATION»»»»" + lsCardNmbr + "»»»»»»";
            
            Webcam.showQR("New G-Card", lsCardNmbr, "new");
            
            lsCardNmbr = "UPDATE G_Card_Application" +
                            " SET cTranStat = '1'" +
                        " WHERE sTransNox = " + SQLUtil.toSQL(poData.getTransNo());
            
            if (poGRider.executeQuery(lsCardNmbr, "G_Card_Application", psBranchCd, "") < 1){
                setMessage("Unable to update GCard Application...\n" + 
                            poGRider.getErrMsg());
                return false;
            }
            
            setMessage("GCard Application was updated successfully...");
            return true;
        } else {
            setMessage((String) loResult.get("error"));
            return false;
        }
    }
    
    public boolean saveUpdate() {
        if(poCtrl == null){
            return false;
        }//if(poCtrl == null)
        else if(pnEditMode == EditMode.UNKNOWN){
            setMessage("Edit mode does not allow saving!");
            return false;
        }//if(poCtrl == null) - else if(pnEditMode == EditMode.UNKNOWN)
        else{
            //verify if if part of the data entry is correct
            if(!isDataOk()){
                //setMessage("Invalid data detected! Please check your entry...");
                return false;
            } 

            //automatically load engine no if new and mc sales/MC2HSales
            if(poData.getApplicationType().equals("1")){ 
                if (poData.getSource().equalsIgnoreCase("M02910000005") || 
                    poData.getSource().equalsIgnoreCase("M02910000012")){ 
                    //auto-detect serial
                    poData.setSerialID(autoSerialID(poData.getTransNo().substring(0, 4), poData.getSourceNo(), poData.getTransactDate()));
                    //verify if serial is loaded successfully...   
                    if(poData.getSerialID().equals("")){
                        setMessage("Unable to load the SERIAL ID! Please verify your entry");
                        return false;
                    }
                } 
            }
            else{
                //if(poData.getSource().equalsIgnoreCase("M02910000007")){   
                if(poData.getPreviousGCard().length() < 13){
                    setMessage("Invalid GCard Number detected! GCard should 13 characters in length");
                    return false;
                }
                //}
            }

            //Perform this part if source is mc sales/e-Replacement/MC2H Sales
            if(poData.getSource().equalsIgnoreCase("M02910000010") ||
                poData.getSource().equalsIgnoreCase("M02910000005") || 
                poData.getSource().equalsIgnoreCase("M02910000012")) {

                JSONObject loJSon = new JSONObject();
                loJSon.put("sSourceNo", (String) poData.getSource());
                
                //Original Code:
                //  loJSon.put("sSerialID", poData.getSerialID());
                
                loJSon.put("sEngineNo", (String) getMCSerial().getMaster("sEngineNo"));
                loJSon.put("sFrameNox", (String) getMCSerial().getMaster("sFrameNox"));
                loJSon.put("sModelNme", (String) getMCModel().getMaster("sModelNme"));                
                
                if(poData.getPoints() instanceof Number)
                    loJSon.put("nPointsxx", poData.getPoints().longValue());
                else
                    loJSon.put("nPointsxx", 0);

                if(poData.getYellow() instanceof Number)
                    loJSon.put("nYellowxx", (int) poData.getYellow());
                else
                    loJSon.put("nYellowxx", 0);

                if(poData.getWhite() instanceof Number)
                    loJSon.put("nWhitexxx", (int) poData.getWhite());
                else
                    loJSon.put("nWhitexxx", 0);

                ServiceCoupon loserv = new ServiceCoupon();
                loserv.setData(loJSon);
                loserv.showGUI();
                if(!loserv.isOkey()){
                    setMessage("Entry was cancelled...");
                    return false;
                }

                //poData.setSerialID((String)loserv.getData().get("sSerialID"));
                poData.setWhite((int)loserv.getData().get("nWhitexxx"));
                poData.setYellow((int)loserv.getData().get("nYellowxx"));
                
                if (loserv.getData().containsKey("nPointsxx"))
                    poData.setPoints((double) loserv.getData().get("nPointsxx"));
                
                if(poData.getSerialID().equals("") &&
                    poData.getWhite() + poData.getYellow() != 0){
                    
                    setMessage("Please enter Engine and/or FSEP values...");
                    return false;               
                }    
            }

            if (!pbWithParnt) poGRider.beginTrans();

            UnitGCApplication loResult;
            if(pnEditMode == EditMode.ADDNEW)
                loResult = (UnitGCApplication) poCtrl.saveUpdate(poData, "");
            else
                loResult = (UnitGCApplication) poCtrl.saveUpdate(poData, (String) poData.getValue(1));

            if(loResult == null){
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
                if (!pbWithParnt) poGRider.rollbackTrans();
                return false;
            }//if(loResult == null)
            else{
                if (!pbWithParnt)  poGRider.commitTrans();
                pnEditMode = EditMode.READY;
                poData = loResult;
                
                setMessage("Transaction saved successfully...");
                return true;
            }//if(loResult == null) - else
        }//if(poCtrl == null) - else
    }

    public boolean deleteTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit Mode does not allow deletion of record!");
            return false;
        }
        else{
            if (!pbWithParnt) poGRider.beginTrans();
 
            boolean lbResult = poCtrl.deleteTransaction(fsTransNox);
            if(lbResult){
                if (!pbWithParnt) poGRider.commitTrans();
                    pnEditMode = EditMode.UNKNOWN;
            }
            else
                if (!pbWithParnt) poGRider.rollbackTrans();
             
            return lbResult;
        }
    }
    
    public boolean releaseCard(){
        if(poCtrl == null){
            return false;
        }
        
        if (!poCtrl.releaseTransaction()){
            setMessage(poCtrl.getMessage());
            return false;
        }
        
        return true;
    }
    
    public boolean closeTransaction(String fsTransNox, String fsCardNmbr) {
        if(poCtrl == null){
            return false;
        }//if(poCtrl == null)
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow verification of application!");
            return false;
        }//if(poCtrl == null)-else if(pnEditMode != EditMode.READY)
        else{
            boolean lbResult = false;

            if(poData.getSource().equalsIgnoreCase("M02910000005")){
                if(poData.getSerialID().equals("")){
                    poData.setSerialID(autoSerialID(poData.getTransNo().substring(0, 4), poData.getSourceNo(), poData.getTransactDate()));
                    //verify if serial is loaded successfully...   
                    if(poData.getSerialID().equals("")){
                        setMessage("Unable to load the SERIAL ID!");
                        return lbResult;
                    }   
                }
                if(pnWhite + pnYellow == 0){
                    setMessage("Invalid FSEP detected!");
                    return lbResult;
                }   
            }
         
            lbResult = true; 

            if (!pbWithParnt) poGRider.beginTrans();

            //kalyptus - 2016.01.13 10:22am
            //run poCtrl.closeTransaction(fsTransNox) first
            if(lbResult) {
                if (fsCardNmbr.isEmpty())
                    lbResult = poCtrl.closeTransaction(fsTransNox);
                else 
                    lbResult = poCtrl.closeTransaction(fsTransNox, fsCardNmbr);
            }
                
            //kalyptus - 2016.01.13 10:22am
            //previously this runs first before poCtrl.closeTransaction(fsTransNox)
            if((!poData.getSerialID().equals("")) && 
                pnWhite + pnYellow != 0){
                //kalyptus - 2016.01.13 10:43am
                //Add GCard NO and date of purchase here...
                String lsService = "INSERT INTO MC_Serial_Service(sSerialID, sGCardNox, nYellowxx, nWhitexxx, dTransact, cRecdStat, cDigitalx)" +
                                     " VALUES( " + SQLUtil.toSQL(poData.getSerialID()) +
                                            ", " + SQLUtil.toSQL(poCtrl.getCardNo()) +                    
                                            ", " + SQLUtil.toSQL(pnYellow) +
                                            ", " + SQLUtil.toSQL(pnWhite) +
                                            ", " + SQLUtil.toSQL(SQLUtil.dateFormat(poData.getTransactDate(), "yyyy-MM-dd")) +
                                            ", " + SQLUtil.toSQL(fsCardNmbr.isEmpty() ? RecordStatus.INACTIVE : RecordStatus.ACTIVE) +
                                            ", " + SQLUtil.toSQL(poData.getDigital()) + ")";

    //            String lsService = "INSERT INTO MC_Serial_Service SET" +
    //                                 "  sSerialID = " + SQLUtil.toSQL(poData.getSerialID()) +
    //                                 ", sGCardNox = " + SQLUtil.toSQL(poCtrl.getCardNo()) +                    
    //                                 ", nYellowxx = " + SQLUtil.toSQL(pnYellow) +
    //                                 ", nWhitexxx = " + SQLUtil.toSQL(pnWhite) +
    //                                 ", dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(poData.getTransactDate(), "yyyy-MM-dd")) +
    //                                 ", cRecdStat = " + SQLUtil.toSQL(RecordStatus.INACTIVE);
                int x = poGRider.executeQuery(lsService, "MC_Serial_Service", "", "");

                //Check if entry is okey
                lbResult = (x != 0); 
            } 
            else if(poData.getSource().equalsIgnoreCase("M02910000005") && 
                    poData.getApplicationType().equalsIgnoreCase("0")){
                if(!updateSerialService(poData.getPreviousGCard())){
                    //kalyptus - 2016.05.24 04:18pm
                    //previously wala po ito...
                    lbResult = false;
                }     
            } 

            if(lbResult){
                if (!pbWithParnt) poGRider.commitTrans();
                setMessage("Application verified successfully!");            
                pnEditMode = EditMode.UNKNOWN;
            }//if(lbResult)
            else{         
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
                if (!pbWithParnt) poGRider.rollbackTrans();
            }//if(!lbResult)

            return lbResult;
        }//if(poCtrl == null)-else
    }

    public boolean postTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }//if(poCtrl == null)
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow posting of application!");          
            return false;
        }//if(poCtrl == null) - else if(pnEditMode != EditMode.READY)
        else{
          
            if (!pbWithParnt) poGRider.beginTrans();
         
         boolean lbResult = poCtrl.postTransaction(fsTransNox);
         if(lbResult){
             setMessage("Application tagged as printed!");             
            if (!pbWithParnt) poGRider.commitTrans();

            pnEditMode = EditMode.UNKNOWN;
         }//if(lbResult)
         else{
            if (!pbWithParnt) poGRider.rollbackTrans();
            
            setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
         }   
         return lbResult;
      }//if(poCtrl == null) - else
   }

    public boolean voidTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow voiding of application!");
            return false;
        }
        else{ 
            if (!pbWithParnt) poGRider.beginTrans();
            boolean lbResult = poCtrl.voidTransaction(fsTransNox);
            if(lbResult){
                if (!pbWithParnt) poGRider.commitTrans();

                setMessage("Application successfully tagged void!");
                pnEditMode = EditMode.UNKNOWN;
            }
            else{
                if (!pbWithParnt) poGRider.rollbackTrans();
            
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
            }   
            return lbResult;
        }
    }

    public boolean cancelTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow the cancellation of application!!");
            return false;
        }
        else{
            if (!pbWithParnt) poGRider.beginTrans();
            boolean lbResult = poCtrl.cancelTransaction(fsTransNox);
            if(lbResult){
                setMessage("Application successfully tagged as cancelled!");
                pnEditMode = EditMode.UNKNOWN;
                if (!pbWithParnt) poGRider.commitTrans();
            }
            else{
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
                if (!pbWithParnt) poGRider.rollbackTrans();
            }   
            return lbResult;
        }
    }

    public boolean SearchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        if(fsValue.trim().length() == 0){
            pnEditMode = EditMode.UNKNOWN;
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSearchSQL();

        //Enable testing of sTransNox regardless of branch to enable
        //searching according to branch
        String lsPrefix = "";
        lsPrefix = psBranchCd;
        lsSQL = MiscUtil.addCondition(lsSQL, "a.sTransNox LIKE " + SQLUtil.toSQL(lsPrefix + "%"));

        if(fsFieldNm.equalsIgnoreCase("sTransNox")){
                if(fsValue.trim().length() <= 0 || fsValue.contains("%"))
                        lsPrefix = "";
                else if(fsValue.length() <= 6)
                        lsPrefix = psBranchCd + SQLUtil.dateFormat(poGRider.getSysDate(), "yy");
                else if(fsValue.length() <= 8)
                        lsPrefix = psBranchCd;

                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
                lsSQL = MiscUtil.addCondition(lsSQL, "a.sTransNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
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
        else if(fsFieldNm.equalsIgnoreCase("sSourceNo")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same source number!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sSourceNo LIKE " + SQLUtil.toSQL(fsValue));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        System.out.println("After doCon");

        if(loCon == null){
            pnEditMode = EditMode.UNKNOWN;
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
                                                            "Client Name»Address»Date",
                                                            "sClientNm»xAddressx»a.dTransact");

                if (loValue != null){
                    lbHasRec = loadTransaction((String) loValue.get("sTransNox"));
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

    public boolean searchField(String field, String value){
        if(field.equalsIgnoreCase("sClientNm")){
           return searchClient(field, value);
        }
        else if(field.equalsIgnoreCase("sClientID")){
           return searchClient(field, value);
        }
        else if(field.equalsIgnoreCase("sModelNme")){
           return searchMCModel(field, value);
        }
        else if(field.equalsIgnoreCase("sModelCde")){
           return searchMCModel(field, value);
        }
        else if(field.equalsIgnoreCase("sModelIDx")){
           return searchMCModel(field, value);
        }
        else if(field.equalsIgnoreCase("sDescript")){
            return searchSource(field, value);
        }
        else if(field.equalsIgnoreCase("sSourceCd")){
           return searchSource(field, value);
        }
        else if(field.equalsIgnoreCase("sBranchNm")){
           return searchBranch(field, value);
        }
        else if(field.equalsIgnoreCase("sBranchCd")){
           return searchBranch(field, value);
        }
        else if(field.equalsIgnoreCase("sEngineNo")){
           return searchMCSerial(field, value);
        }
        else if(field.equalsIgnoreCase("sFrameNox")){
           return searchMCSerial(field, value);
        }
        else if(field.equalsIgnoreCase("sSerialID")){
           return searchMCSerial(field, value);
        }
        else{
            setMessage("Invalid search field [" + field + "]  detected!");
           return false;
        }
    }

    private boolean searchClient(String fsFieldNm, String fsValue){
        System.out.println("Inside searchClient");
        fsValue = fsValue.trim();
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSQL_Client();
        if(fsFieldNm.equalsIgnoreCase("sClientID")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Client ID!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sClientID LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                             ((String) poData.getValue("sFrstName")) + " " +
                             ((String) poData.getValue("sMiddName"));
            if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                setMessage("The same client name!");
                return false;
            }
            
            //mac 2019.07-01
            //String []laNames = MiscUtil.splitName(fsValue);
            //lsSQL = MiscUtil.addCondition(lsSQL, "b.sLastName LIKE " + SQLUtil.toSQL(laNames[0]) +
            //                                " AND CONCAT(b.sFrstname, IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(' ', b.sSuffixNm))) LIKE " + SQLUtil.toSQL(laNames[1]) +
            //                                " AND b.sMiddName LIKE " + SQLUtil.toSQL(laNames[2]));
            
            lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        System.out.println("After doCon");

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

            System.out.println(lsSQL);
            if(!loRS.next()){
                setMessage("No record found...");
                poData.setClientID("");
            }else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "ID»Name»Birthday»Birth Place»Address", 
                                                                "sClientID»sClientNm»dBirthDte»xBirthPlc»xAddressx");
              
                if (loValue != null){
                    poData.setClientID((String) loValue.get("sClientID"));
                    lbHasRec = true;
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

    private boolean searchSource(String fsFieldNm, String fsValue){
        System.out.println("Inside searchSource");
        fsValue = fsValue.trim();

        String lsSQL = getSQL_Source();
        if(fsFieldNm.equalsIgnoreCase("sDescript")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Source Description!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sDescript LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sSourceCd"))
        {
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Source Code!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sSourceCd LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sDescCode"))
        {
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Source Description Code!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sDescCode LIKE " + SQLUtil.toSQL(fsValue));
        }

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        System.out.println("After doCon");

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
               System.out.println(lsSQL);
               loRS = loStmt.executeQuery(lsSQL);

                if(!loRS.next()){
                    setMessage("No record found...");
                    poData.setSource("");
                }else{
                    JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Code»Description»Short Desc", 
                                                                "sSourceCd»sDescript»sDescCode");
              
                    if (loValue != null){
                        poData.setSource((String) loValue.get("sSourceCD"));
                        lbHasRec = true;
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

    private boolean searchBranch(String fsFieldNm, String fsValue){
        System.out.println("Inside searchBranch");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSQL_Branch();
        if(fsFieldNm.equalsIgnoreCase("sBranchNm")){
//         if(fsValue.trim().equalsIgnoreCase((String) getBranch().getMaster(fsFieldNm))){
//            System.out.println("The same Branch Name!");
//            return false;
//         }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sBranchCd")){
//         if(fsValue.trim().equalsIgnoreCase((String) getBranch().getMaster(fsFieldNm))){
//            System.out.println("The same Branch Code!");
//            return false;
//         }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchCd LIKE " + SQLUtil.toSQL(fsValue));
        }

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
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);

            if(loRS.next()){
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Code»Description", 
                                                                "sBranchCd»sBranchNm");
              
                if (loValue != null){
                    setBranch((String) loValue.get("sBranchCd"));
                    lbHasRec = true;
                } else
                    setMessage("No record selected...");
            } else
                setMessage("No record found...");

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

    private boolean searchMCModel(String fsFieldNm, String fsValue){
        System.out.println("Inside searchMCModel");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSQL_MCModel();
        if(fsFieldNm.equalsIgnoreCase("sModelNme")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Model Name!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sModelNme LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sModelCde")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Model Code!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sModelCde LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sModelIDx")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Model ID!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sModelIDx LIKE " + SQLUtil.toSQL(fsValue));
        }

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
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);
		 
            if(!loRS.next()){
                setMessage("No record found...");
                poData.setModelID("");
            }else{                
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "ID»Description»Code", 
                                                                "sModelIDx»sModelNme»sModelCde");
              
                if (loValue != null){
                    poData.setModelID((String) loValue.get("sModelIDx"));
                    lbHasRec = true;
                } else
                    setMessage("No record selected.");
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

    private boolean searchMCSerial(String fsFieldNm, String fsValue){
        System.out.println("Inside searchMCSerial");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!"); 
            return false;
        }

        String lsSQL = getSQL_MCSerial();
        if(fsFieldNm.equalsIgnoreCase("sEngineNo")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Engine No!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sEngineNo LIKE " + SQLUtil.toSQL("%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sFrameNox")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                System.out.println("The same Frame No!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sFrameNox LIKE " + SQLUtil.toSQL("%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sSerialID")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Serial ID!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sSerialID LIKE " + SQLUtil.toSQL(fsValue));
        }

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
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);

            if(!loRS.next()){
                setMessage("No record found...");
                poData.setSerialID("");
            }else{               
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "ID»Engine No.»Frame No.", 
                                                                "sSerialID»sEngineNo»sFrameNox");
              
                if (loValue != null){
                    poData.setSerialID((String) loValue.get("sSerialID"));
                    lbHasRec = true;
                }else 
                    setMessage("No record selected.");
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
   
    private boolean isDataOk(){
        //test for the validity of the different fields here
        if (poData.getClientID().equals("")) {
            setMessage("Invalid Customer Detected");
            return false;
        }

        //mac 2019.0726
        //  uncomment this if you want to abort saving if source is empty
        //if (poData.getSourceNo().equals("")) {
        //    setMessage("Invalid Source Document Number Detected");
        //    return false;
        //}

        //kalyptus - 2017.08.10 09:30am
        //Please review checking of GCard Type later...
        if (!poData.getCardType().equals("0")) {
            setMessage("We are currently accepting Premiums only...");
            return false;
        }
        
        //mac 2019.07.26
        //  check source, if empty don't save
        if (poData.getSource().equals("")){
            setMessage("Application source must not be empty.");
            return false;
        }
      
        //kalyptus - 2017.08.09 04:00pm
        //Is Application source from MC Sales
        if (poData.getSource().equalsIgnoreCase("M02910000005")) {
            if(!poData.getApplicationType().equals("1")){
                setMessage("MC Sales should be use for NEW GCard Application only");
                return false;
            }
        }
        
        //mac 2019.07.26
        //Is Application source from 2H MC Sales
        if (poData.getSource().equalsIgnoreCase("M02910000012")) {
            if(!poData.getApplicationType().equals("1")){
                setMessage("MC-2H Sales should be use for NEW GCard Application only");
                return false;
            }
        }

        //kalyptus - 2017.08.09 04:00pm
        //Is Application source from eReplacement/Officrs
        if (poData.getSource().equalsIgnoreCase("M02910000010") || poData.getSource().equalsIgnoreCase("M02910000009")) {
            if(!poData.getApplicationType().equals("1")){
                setMessage("eReplacement/Officers should be use for NEW GCard Application only");
                return false;
            }
         
            //Serial ID should not be empty of eReplacement...
            if(poData.getSource().equalsIgnoreCase("M02910000010")){
                if(poData.getSerialID().isEmpty()){
                    setMessage("Engine No of eReplacement GCard Application should not be empty...");                
                    logwrapr.severe("Engine No of eReplacement GCard Application should not be empty...");
                    return false;
                }
            }
        }
      
        //kalyptus - 2017.08.09 04:00pm
        //Is Application source from Receipt
        if (poData.getSource().equalsIgnoreCase("M02910000007")) {
            //Is it new
            if(poData.getApplicationType().equals("1")){
                setMessage("We are not accepting NEW GCard Application from Receipt");
                return false;
            } 
            //Is It Renewal
            else if(poData.getApplicationType().equals("2")){
                if(poData.getAmountPaid() <= 0){
                    setMessage("Please enter the amount paid for Renewal...");
                   return false;
                }

                if(!poData.getPurchaseMode().equals("4")){
                    if (!isReceiptOk(poData.getTransNo().substring(0, 4), poData.getSourceNo(), poData.getTransactDate())){
                        setMessage("Receipt GCard Application should have a Receipt Transaction...");
                        logwrapr.severe("Receipt GCard Application should have a Receipt Transaction...");
                        return false;
                    }
                }
            } 
        //Is It a Replacement
        else{
            if(poData.getAmountPaid() > 0){
                setMessage("If a client paid, the transaction type should be RENEWAL!");
                return false;
            }
            
            if(MsgBox.showYesNo("A REPLACEMENT transaction needs an approval from the GCARD Division?\n" + 
                               " Do you have an approval from GCARD Division?") == MsgBox.RESP_NO) 
                return false;
            }
         
            //Make sure Previous GCard Is not empty...
            if(poData.getPreviousGCard().equals("")){
                setMessage("UNSET Previous GCard Detected");             
                return false;
            }         

            //Check the validity of GCard here...
            //If not valid the reason was log from isPrevGCardOk
            if(!isPrevGCardOk(poData.getPreviousGCard())){
                setMessage("Invalid Previous GCard Detected");             
                return false;
            }

            //Check the if GCard was impounded...
            if(isImpounded(poData.getPreviousGCard())){
                setMessage("Invalid Previous GCard Detected");             
                return false;
            }
        }
      
        if (poData.getTransactDate() == null){
            setMessage("Invalid Transaction Date Detected");
            return false;
        }

//      //If application type is renewal OR replacement
//      if(poData.getApplicationType().equals("2") || 
//         poData.getApplicationType().equals("0") ){
//          if(poData.getPreviousGCard().equals("")){
//              MsgBox.showOk("Invalid Previous GCard Detected");
//              return false;
//          }
//      }       

        if(poData.getNameOnCard().equals("")){
            setMessage("Invalid Name On Card Detected");
           return false;
        }
              
        return true;
    }

    private boolean isReceiptOk(String sBranchCd, String sORNoxxxx, Date dTransact) {
        Connection loCon = poGRider.getConnection();
        boolean lbResult = false;
        String lsSQL = "SELECT a.sTransNox" +
                        " FROM Receipt_Master a" +
                        " WHERE a.sTransNox LIKE " + SQLUtil.toSQL(sBranchCd + "%") +
                          " AND a.sORNoxxxx = " + SQLUtil.toSQL(sORNoxxxx) +
                          " AND a.dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(dTransact, "yyyy-MM-dd")) + 
                          " AND a.cTranType = '9'" + 
                          " AND a.cTranStat <> '3'"; 
            
        System.out.println(lsSQL);
        Statement loStmtSerial = null;
        ResultSet loRSSerial = null;

        try {
            loStmtSerial = loCon.createStatement();
         
            loRSSerial = loStmtSerial.executeQuery(lsSQL);
            if(loRSSerial.next()){
                lbResult = true;
            }
        }
        catch (SQLException ex) {
            logwrapr.severe("isReceiptOk: error in SQL statement", ex);
        }//catch (SQLException ex)         
        finally{
            MiscUtil.close(loStmtSerial);
            MiscUtil.close(loRSSerial);
            if(poGRider.getConnection() == null)
                MiscUtil.close(loCon);
        }
      
        return lbResult;
    }
   
    private boolean isPrevGCardOk(String fsPrevGCrd){
        boolean lbResult = false;
        try {
            StringBuilder lsSQL = new StringBuilder();
            lsSQL.append("SELECT sApplicNo, cCardStat")
                    .append(" FROM G_Card_Master") 
                    .append(" WHERE sCardNmbr = " + SQLUtil.toSQL(fsPrevGCrd)); 

            ResultSet loRS = poGRider.getConnection().createStatement().executeQuery(lsSQL.toString());
         
            if(!loRS.next()){
                logwrapr.severe("GCard Not found: " + fsPrevGCrd);
            }
         
            //kalyptus - 2017.08.18 01:24pm
            //test for the status of GCard if valid for replacement/renewal
            if(loRS.getString("cCardStat").equals("0")){
                logwrapr.severe("GCard is still open");
            }
            else if(loRS.getString("cCardStat").equals("1")){
                logwrapr.severe("GCard was just printed");
            }
            else if(loRS.getString("cCardStat").equals("6")){
                logwrapr.severe("GCard was already replaced");
            }
            else if(loRS.getString("cCardStat").equals("7")){
                logwrapr.severe("GCard was already renewed");
            }
            else
                lbResult = true;
        } catch (SQLException ex) {
            logwrapr.severe("isPrevGCardOk: SQLException error detected.", ex);
        }
      
        return lbResult;
    }
   
    private boolean isImpounded(String fsPrevGCrd){
        String lsSQL;
        Connection loCon = null;
        Statement loStmt = null;
        ResultSet loRS = null;
        boolean lbImpounded = true;
      
        loCon = poGRider.getConnection();

        lsSQL = "SELECT c.sAcctNmbr, c.dRedeemxx" + 
                " FROM G_Card_Master d" + 
                    " LEFT JOIN G_Card_Application a ON d.sApplicNo = a.sTransNox" + 
                    " LEFT JOIN MC_AR_Master b ON a.sSerialID = b.sSerialID" + 
                    " LEFT JOIN Impound c ON b.sAcctNmbr = c.sAcctNmbr" + 
                " WHERE d.sCardNmbr = " + SQLUtil.toSQL(fsPrevGCrd) +  
                    " AND c.dRedeemxx IS NULL" + 
                " ORDER BY c.sAcctNmbr DESC";
      
        try {
            loStmt = loCon.createStatement();
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);

            if(!loRS.next())
                lbImpounded =  false;
            else{
                if(loRS.getString("sAcctNmbr") == null)
                    lbImpounded = false;
                else{
                    if(loRS.getDate("dRedeemxx") == null)
                        lbImpounded = true;
                    else
                    lbImpounded = false;
                }
            }
        } catch (SQLException ex) {
            logwrapr.severe("isImpounded: SQLException error detected.", ex);
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
       
        return lbImpounded;
    }
   
    private String autoSerialID(String sBranchCd, String sDRNoxxxx, Date dTransact) {
        StringBuffer sSerialID = new StringBuffer("");
        Connection loCon = poGRider.getConnection();
      
        String lsSQL = "SELECT sSerialID, sReplMCID" +
                        " FROM MC_SO_Detail" +
                        " WHERE sTransNox IN(" +
                            " SELECT sTransNox" +
                            " FROM MC_SO_Master" +
                            " WHERE sTransNox LIKE " + SQLUtil.toSQL(sBranchCd + "%") +
                                " AND sDRNoxxxx = " + SQLUtil.toSQL(sDRNoxxxx) +
                                " AND dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(dTransact, "yyyy-MM-dd")) + ")" + 
                                " AND sSerialID <> ''";
            
        System.out.println(lsSQL);
        Statement loStmtSerial = null;
        ResultSet loRSSerial = null;

        try {
            loStmtSerial = loCon.createStatement();
         
            loRSSerial = loStmtSerial.executeQuery(lsSQL);
            //kalyptus - 2017.05.09 04:09pm
            //Do not allow creation of application for MC Sales Replacement
            if(loRSSerial.next()){
                if(loRSSerial.getString("sReplMCID").isEmpty())
                    sSerialID.append(loRSSerial.getString("sSerialID"));
                else{
                    if(pnEditMode == EditMode.READY){
                        sSerialID.append(loRSSerial.getString("sSerialID"));
                    }
                }
            }
        }
        catch (SQLException ex) {
            Logger.getLogger(XMGCApplication.class.getName()).log(Level.SEVERE, null, ex);
            MsgBox.showOk(ex.getMessage());
        }//catch (SQLException ex)         
        finally{
            MiscUtil.close(loStmtSerial);
            MiscUtil.close(loRSSerial);
            if(poGRider.getConnection() == null)
                MiscUtil.close(loCon);
        }
      
        return sSerialID.toString();
    }
   
    //kalyptus - 2016.04.19 01:23pm
    //On Card Renewal CHANGE the Card No of MC_Serial_Service
    private boolean updateSerialService(String fsValue){
        System.out.println("Inside searchSource");
        fsValue = fsValue.trim();
        if(fsValue.trim().length() == 0){
            fsValue = "%";
        }

        String lsSQL = "SELECT a.sGCardNox" +
                          ", b.*" + 
                        " FROM G_Card_Master a" + 
                            " LEFT JOIN MC_Serial_Service b ON a.sGCardNox = b.sGCardNox" + 
                        " WHERE a.sCardNmbr = " + SQLUtil.toSQL(fsValue) + 
                        " AND b.cRecdStat IN ('0', '1')";

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        System.out.println("After doCon");

        if(loCon == null){
            setMessage("Invalid connection!");
            logwrapr.severe("updateSerialService: Invalid connection when extracting previous GCard No:" + fsValue + " detected. With Trans No. " + (String) poData.getTransNo());
            return false;
        }

        boolean lbHasRec = false;
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            System.out.println("Before Execute");

            loStmt = loCon.createStatement();
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);
         
            while(loRS.next()){
                lsSQL = "UPDATE MC_Serial_Service" + 
                        " SET sGCardNox = " + SQLUtil.toSQL(poCtrl.getCardNo()) + 
                        " WHERE sGCardNox = " + SQLUtil.toSQL(loRS.getString("sGCardNox")); 
                poGRider.executeQuery(lsSQL, "MC_Serial_Service", "", "");
            }

            //Kalyptus - 2017.08.23 10:41am
            //transferred back here since the fsec of renewed card may have been already 
            //expired. or since it was already expired from the previous renewal the 
            //new renewal should no longer change the new gcard of the expired fsec.
            lbHasRec = true;
        } catch (SQLException ex) {
            //Kalyptus - 2016.04.29 04:29pm
            //Include a code to update SQLException 
            logwrapr.severe("updateSerialService: SQLException when extracting previous GCard No:" + fsValue + " detected. With Trans No. " + (String) poData.getTransNo(), ex);
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
      
        if(!lbHasRec){ 
            //Kalyptus - 2016.04.29 04:29pm
            //Include a code to inform us that no record was found during the update of serial 
            logwrapr.severe("updateSerialService: No record found from MC_Serial_Service when extracting previous GCard No:" + fsValue + ". With Trans No. " + (String) poData.getTransNo());
        }
        return lbHasRec;
    }
   
    public void setBranch(String fsBranchCD) {
        psBranchCd = fsBranchCD;
        poCtrl.setBranch(fsBranchCD);
    }

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }

    public int getEditMode() {
        return pnEditMode;
    }

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        String lsClientID = poData.getClientID();
        if(lsClientID.isEmpty() || lsClientID == null)
            poClntx.newRecord();
        else
            poClntx.openRecord(poData.getClientID());
        return poClntx;
    }

    public XMMCModel getMCModel(){
        if(poMCModel == null)
            poMCModel = new XMMCModel(poGRider, psBranchCd, true);

        poMCModel.openRecord(poData.getModelID());
        return poMCModel;
    }

    public XMMCSerial getMCSerial(){
        if(poMCSerial == null)
            poMCSerial = new XMMCSerial(poGRider, psBranchCd, true);

        poMCSerial.openRecord(poData.getSerialID());
        return poMCSerial;
    }
   
    public XMBranch getBranch(){
        if(poBranch == null)
            poBranch = new XMBranch(poGRider, psBranchCd, true);

        poBranch.openRecord(psBranchCd);
        return poBranch;
    }

    public XMGCPointBasis getSource(){
        if(poSource == null)
            poSource = new XMGCPointBasis(poGRider, psBranchCd, true);

        poSource.openRecord(poData.getSource());
        return poSource;
    }
   
    public String getSearchSQL(){       
        return "SELECT" +
                    "  a.sTransNox" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dTransact" +
                " FROM G_Card_Application a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                " ORDER BY sTransNox DESC";  

        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
        //            " WHERE a.sTransNox LIKE " + SQLUtil.toSQL(psBranchCd + "%");
    }

    private String getSQL_Source(){
        return "SELECT" +
                    "  sSourceCD" +
                    ", sDescCode" +
                    ", sDescript" +
                " FROM G_Card_Points_Basis" +
                " WHERE cSignedUp = '1'" +
                    " AND cRecdStat = '1'";
    }   
    
    private String getSQL_Client(){
        return "SELECT" +
                    "  a.sClientID" +
                    ", CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName) sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", CONCAT(e.sTownName, ' ', f.sProvName, ' ', e.sZippCode) xBirthPlc" +
                    ", a.dBirthDte" +
                " FROM Client_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                    " LEFT JOIN TownCity e ON b.sBirthPlc = e.sTownIDxx" +
                    " LEFT JOIN Province f ON e.sProvIDxx = f.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE);
    }

    private String getSQL_Branch(){
        return "SELECT" +
                    "  sBranchCd" +
                    ", sBranchNm" +
                " FROM Branch" +
                " WHERE cRecdStat = '1'";
    }

    private String getSQL_MCModel(){
        return "SELECT" +
                    "  sModelIDx" +
                    ", sModelNme" +
                    ", sModelCde" +
                " FROM MC_Model" +
                " WHERE cRecdStat = '1'";
    }

    private String getSQL_MCSerial(){
        return "SELECT" +
                    "  sSerialID" +
                    ", sEngineNo" +
                    ", sFrameNox" +
                " FROM MC_Serial"; 
    }
      
    private XMClient poClntx = null;
    private XMMCModel poMCModel = null;
    private XMMCSerial poMCSerial = null;
    private XMBranch poBranch = null;
    private XMGCPointBasis poSource = null;

    private static LogWrapper logwrapr = new LogWrapper("XMGCApplication", "temp/XMGCApplication.log");
   
    private UnitGCApplication poData;
    private GCApplication poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
    private boolean pbWithParnt = false;
    private int pnWhite = 0;
    private int pnYellow = 0;
   

    public void setWhite(int white) {
        pnWhite = white;
    }

    public void setYellow(int yellow) {
        pnYellow = yellow;
    }

    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
}

