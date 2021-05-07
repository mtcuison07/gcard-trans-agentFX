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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.GCardStatus;
import org.rmj.appdriver.constants.UserRight;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.gcard.validator.point.GCardPoint;
import org.rmj.replication.utility.LogWrapper;
import org.rmj.appdriver.agent.MsgBox;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.gcard.agent.misc.XMGCPointBasis;
import org.rmj.gcard.device.ui.GCardDevice;
import org.rmj.gcard.device.ui.GCardDeviceFactory;
import org.rmj.gcard.service.GCLoadInfo;
import org.rmj.gcard.trans.GCOffPoints;
import org.rmj.integsys.pojo.UnitGCardDetailOffline;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
public class XMGCOffPoints {
    public XMGCOffPoints(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCOffPoints();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;
            
            ClearGCardProperty();
        }
    }

    public void setMaster(String fsCol, Object foData){
        setMaster(poData.getColumn(fsCol), foData);
    }

    public void setMaster(int fnCol, Object foData) {
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
            // Don't allow update for sGCardNox, cCardStat, sModified, and dModified
            if(!(fnCol == poData.getColumn("sTransNox") ||
                fnCol == poData.getColumn("nPointsxx") ||
                fnCol == poData.getColumn("cTranStat") ||
                fnCol == poData.getColumn("sPostedxx") ||
                fnCol == poData.getColumn("dPostedxx") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){

                if(fnCol == poData.getColumn("dTransact")){
                    System.out.println("Date is " + foData); 
                    if(foData instanceof Date)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, null);
                    System.out.println("Date is " + poData.getValue(fnCol));
                }
                else if(fnCol == poData.getColumn("nTranAmtx")){
                    if(foData instanceof Number)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, 0.00);

                    Double amt = (Double) poData.getValue("nTranAmtx");
                    String code = (String) poData.getValue("sSourceCd");
                    
                    setPoints(code, amt, pbHasBonus);
                }
                else if(fnCol == poData.getColumn("sSourceCD")){
                    Double amt = (Double) poData.getValue("nTranAmtx");
                    String code = (String) poData.getValue("sSourceCd");

                    setPoints(code, amt, pbHasBonus);

                }
                else if(fnCol == poData.getColumn("sSourceNo")){
                    Double amt = (Double) poData.getValue("nTranAmtx");
                    String code = (String) poData.getValue("sSourceCd");

                    poData.setValue(fnCol, foData);
                    
                    setPoints(code, amt, pbHasBonus);
                }
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

        poData = (UnitGCardDetailOffline) poCtrl.newTransaction();

        if(poData == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poGCard = null;
            poSource = null;
            poBranch = null;

            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean loadTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCardDetailOffline) poCtrl.loadTransaction(fsTransNox);

        if(poData.getTransNo() == null){
            return false;
        }
        else{            
            //set the values of foreign key(object) to null
            poGCard = null;
            poBranch = null;
            poSource = null;

            pnEditMode = EditMode.READY;
            return true;
        }
    }   

    public boolean saveUpdate() {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode == EditMode.UNKNOWN){
            return false;
        }
        else{
            //Only regular GCard and/or Member card earn points...
            if(((String)this.getGCard().getMaster("cMainGrpx")).contentEquals("1")){
                setMessage("Mother GCard cannot earn points!");            
                return false;
            }
         
            String validator = poGRider.getValidator("GCardPoint", poData.getSourceCd());
            if(validator == null){
               setMessage("Invalid validator detected... \n" + 
                           "Verification of transaction failed!");
               return false;
            }
         
            long point = 0;
            GCardPoint valid = (GCardPoint) MiscUtil.createInstance(validator);
            valid.setGRider(poGRider);
            valid.setData(poData);
            valid.checkSource();
            if(valid.getPoints() <= 0){
                if(valid.getMessage().length() > 0){
                    setMessage(valid.getMessage() + "\nVerification of transaction failed!");
                } else
                    setMessage("Verification of transaction failed!");
                
                return false;
            }

            point = valid.getPoints();
            if(isInGCOnline()){
                point = 0;
            }
            else if(isInGCOffline()){
                point = 0;
            }         

            if((double)point != poData.getPoints()){
                setMessage("Points validation failed...\n" +
                           "Verification of transaction failed!");
               return false;
            }
            
            //mac 2020-07-25
            //  employee G-Card must not earn points
            if (isEmployee()){
                setMessage("Employees are not ALLOWED to earn POINTS.");
                return false;
            }
         
            if (!pbWithParnt) poGRider.beginTrans();
         
            UnitGCardDetailOffline loResult;
            if(pnEditMode == EditMode.ADDNEW)
                loResult = (UnitGCardDetailOffline) poCtrl.saveUpdate(poData, "");
            else
                loResult = (UnitGCardDetailOffline) poCtrl.saveUpdate(poData, (String) poData.getValue(1));

            if(loResult == null){
                if (!pbWithParnt) poGRider.rollbackTrans();
                setMessage(poCtrl.getErrMsg() + " " + poCtrl.getMessage());
                return false;
            }
            else{
                pnEditMode = EditMode.READY;
                poData = loResult;
                if (!pbWithParnt) poGRider.commitTrans();
                
                setMessage("Transaction saved successfully...");
                return true;
            }
        }
    }

    public boolean deleteTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
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

    public boolean closeTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow verification of transaction!");
            return false;
        }
        else{            
            //mac 2020-06-19
            //check if server is active
            if (!CommonUtils.isURLOnline(CommonUtils.getConfiguration(poGRider, "WebSvr"))){
                setMessage("Main office server cannot be reached. You can only VERIFY an offline points entry when server is ONLINE.");
                return false;
            }
            
            //Assign the verification to the manager...   
            if(poGRider.getUserLevel() < UserRight.MANAGER){
                setMessage("Verification of transaction should be for managers only!");
                return false;
            } 

            String validator = poGRider.getValidator("GCardPoint", poData.getSourceCd());
            if(validator == null){
                setMessage("Invalid validator detected...\n" +
                            "Verification of transaction failed!");
                return false;
            }
         
            long point = 0;
            GCardPoint valid = (GCardPoint) MiscUtil.createInstance(validator);
            valid.setGRider(poGRider);
            valid.setData(poData);
            valid.checkSource();
            if(valid.getPoints() <= 0){
                if(valid.getMessage().length() > 0){
                    setMessage(valid.getMessage() + "\nVerification of transaction failed!");
                }
                else
                    setMessage("Verification of transaction failed!");            
                
                return false;
            }

            point = valid.getPoints();

            if(isInGCOnline()){
                point = 0;
            }
            else if(isInGCOffline()){
                point = 0;
            }
         
            System.out.println((double)point);
            System.out.println(poData.getPoints());
         
            if((double)point != poData.getPoints()){
                setMessage("Points validation failed...\n" +
                            "Verification of transaction failed!");
                return false;
            }
            
            //mac 2020.06.16
            //add otp validation
            //connect card first
            if (!connectCard()) return false;
            
            //compare if the card number was the same
            if (!System.getProperty("app.gcard.no").equals(poData.getGCardNo())){
                setMessage("Connected card is not the same card on the offline transaction.");
                return false;
            }
            //request for otp
            
            if(null == poGCDevice.UIDeviceType()){
                poData.setOTPassword("");
            } else switch (poGCDevice.UIDeviceType()) {
                case QRCODE:
                    poData.setOTPassword(String.format("%06d", MiscUtil.getRandom(999999)));
                    break;
                case NONE:
                    //mac 2020-0609
                    //  added no chip card for otp generation.
                    poData.setOTPassword(String.format("%06d", MiscUtil.getRandom(999999)));
                    break;
                default:
                    poData.setOTPassword("");
                    break;
            }
            
            JSONObject poJson = new JSONObject();
            poJson.put("SOURCE", "OFFLINE");
            poJson.put("sOTPasswd", (String)poData.getValue("sOTPasswd"));
            
            JSONArray poArr = new JSONArray();
            JSONObject poDtl = new JSONObject();
            poDtl.put("sTransNox", poData.getTransNo());
            poDtl.put("dTransact", poData.getTranDate());
            poDtl.put("sSourceNo", poData.getSourceNo());
            poDtl.put("sSourcexx", poData.getSourceCd()); //source code
            poDtl.put("sSourceCD", (String)this.getSource().getMaster("sDescript")); //sDescCode
            poDtl.put("nTranAmtx", poData.getTranAmount());
            poDtl.put("nPointsxx", poData.getPoints());
            
            poArr.add(poDtl);
            poJson.put("DETAIL", poArr);
            poGCDevice.setTransData(poJson);
            
            if(!poGCDevice.write()){
                setMessage(poGCDevice.getMessage());
                return false;
            }
            //END - add otp validation

            if (!pbWithParnt) poGRider.beginTrans();
         
            if(!valid.SaveOthers()){
                setMessage(valid.getMessage() + "\n" +
                            "Verification of transaction failed!");
                return false;
            }

            //boolean lbResult = poCtrl.closeTransaction(fsTransNox);
            //mac 2020.06.19
            boolean lbResult = poCtrl.closeTransactionOnline(fsTransNox);
            
            //disconnect the card
            poGCDevice.release();
            
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if (!pbWithParnt) 
                    poGRider.commitTrans();
            }
            else{
                setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());
                if (!pbWithParnt) 
                    poGRider.rollbackTrans();
            }

            return lbResult;
        }
    }

    public boolean postTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if (!pbWithParnt) poGRider.beginTrans();
 
            boolean lbResult = poCtrl.postTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if (!pbWithParnt) poGRider.commitTrans();
            }
            else
                if (!pbWithParnt) poGRider.rollbackTrans();

            return lbResult;
        }
    }

    public boolean voidTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if (!pbWithParnt) poGRider.beginTrans();
            boolean lbResult = poCtrl.voidTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if (!pbWithParnt) poGRider.commitTrans();
            }
            else{
                setMessage(poCtrl.getMessage());
                if (!pbWithParnt) poGRider.rollbackTrans();
            }

            return lbResult;
        }
    }

    public boolean cancelTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if (!pbWithParnt) poGRider.beginTrans();
            boolean lbResult = poCtrl.cancelTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if (!pbWithParnt) poGRider.commitTrans();
            }
            else{
                setMessage(poCtrl.getMessage());
                if (!pbWithParnt) poGRider.rollbackTrans();
            }
                
             
            return lbResult;
        }
    }

    public boolean searchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSQL_Master();
        if(fsFieldNm.equalsIgnoreCase("sTransNox")){
            String lsPrefix = "";
            if(fsValue.trim().length() <= 0 || fsValue.contains("%"))
                lsPrefix = "";
            else if(fsValue.length() <= 6)
                lsPrefix = psBranchCd + SQLUtil.dateFormat(poGRider.getSysDate(), "yy");
            else if(fsValue.length() <= 8)
                lsPrefix = psBranchCd;
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sTransNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            if(pnEditMode != EditMode.UNKNOWN){
                String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                                    ((String) poData.getValue("sFrstName")) + " " +
                                    ((String) poData.getValue("sMiddName"));
                if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                    setMessage("The same client name!");
                    return false;
                }
            }
            
            lsSQL = MiscUtil.addCondition(lsSQL, "b.sCompnyNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
            
            //lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sSourceNo")){
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
            }

            lsSQL = MiscUtil.addCondition(lsSQL, "a.sSourceNo LIKE " + SQLUtil.toSQL(fsValue));
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
            
            ClearGCardProperty();
            
            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Client Name»Address»Card Number»Date»Source No»Description", 
                                                                "sClientNm»xAddressx»f.sCardNmbr»a.dTransact»a.sSourceNo»e.sDescript");
              
                if (loValue != null){                    
                    if (loadTransaction((String) loValue.get("sTransNox"))){
                        JSONObject loJSON = GCLoadInfo.GetCardInfoOnline(poGRider, (String) loValue.get("sCardNmbr"));
                        
                        if ("success".equalsIgnoreCase((String) loJSON.get("result"))){
                            System.setProperty("app.gcard.no", (String) loJSON.get("sGCardNox"));
                            System.setProperty("app.card.no", (String) loJSON.get("sCardNmbr"));
                            System.setProperty("app.gcard.holder", (String) loJSON.get("sCompnyNm"));
                            System.setProperty("app.client.id", (String) loJSON.get("sClientID"));
                            System.setProperty("app.device.type", (String) loJSON.get("cDigitalx"));
                            System.setProperty("app.gcard.mobile", (String) loJSON.get("sMobileNo"));
                        } else {
                            loJSON = GCLoadInfo.GetCardInfoOffline(poGRider, (String) loValue.get("sCardNmbr"));
                            System.setProperty("app.gcard.no", (String) loJSON.get("sGCardNox"));
                            System.setProperty("app.card.no", (String) loJSON.get("sCardNmbr"));
                            System.setProperty("app.gcard.holder", (String) loJSON.get("sCompnyNm"));
                            System.setProperty("app.client.id", (String) loJSON.get("sClientID"));
                            System.setProperty("app.device.type", (String) loJSON.get("cDigitalx"));
                            System.setProperty("app.gcard.mobile", (String) loJSON.get("sMobileNo"));
                        }
                        lbHasRec = true;        
                    }               
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
    
    public void ClearGCardProperty(){
        System.setProperty("app.card.connected", "");
        System.setProperty("app.gcard.no", "");
        System.setProperty("app.gcard.holder", "");
        System.setProperty("app.card.no", "");
        System.setProperty("app.device.type", "");
        System.setProperty("app.device.data", "");
        System.setProperty("app.client.id", "");
        System.setProperty("app.gcard.online", "");
    } 

    public boolean searchField(String field, String value){
        if(field.equalsIgnoreCase("sCardNmbr")){
            return searchClient(field, value);
        }
        else if(field.equalsIgnoreCase("sClientNm")){
            return searchClient(field, value);
        }
        else if(field.equalsIgnoreCase("sGCardNox")){
            return searchClient(field, value);
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
        else if(fsFieldNm.equalsIgnoreCase("sGCardNox"))
        {
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same card number!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sGCardNox LIKE " + SQLUtil.toSQL(fsValue));
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
            loRS = loStmt.executeQuery(lsSQL);

            System.out.println(lsSQL);
            if(!loRS.next()){
                setMessage("No record found...");
                poData.setGCardNo("");
            }else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Name»Address»Card Number", 
                                                                "sClientNm»xAddressx»a.sCardNmbr");
              
                if (loValue != null){
                    //mac 2019.10.07
                    //  check if the card was activated.
                    if (!"4".equals((String) loValue.get("cCardStat"))){
                        setMessage("G-Card is not activated. Please activate the card to continue...");
                    } else {
                        poData.setGCardNo((String) loValue.get("sGCardNox"));
                        System.setProperty("app.client.id", (String) loValue.get("sClientID"));
                        
                        System.out.println("Client ID: " + System.getProperty("app.client.id"));
                        lbHasRec = true;
                    }
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
        System.out.println(lbHasRec);
        return lbHasRec;
    }

    private boolean searchSource(String fsFieldNm, String fsValue){
        System.out.println("Inside searchSource");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
           fsValue = "%";
        }

        //Make sure that a card has been inserted before searching for a
        //type of transaction!
        if(poData.getGCardNo().isEmpty()){
            setMessage("No card was connected!");
            return false;
        }

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
            lsSQL = MiscUtil.addCondition(lsSQL, "sDescCode LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        String lcCardType = (String) poGCard.getMaster("cCardType");
        //Filter the type of transaction here accdg to the GCard Type
        lsSQL = MiscUtil.addCondition(lsSQL, "cCardType = " + SQLUtil.toSQL(lcCardType));
        //TODO:Filter the type of transaction accdg to branch's company

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

            if(!loRS.next())
                poData.setSourceCd("");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                            "Code»Description»Short Desc", 
                                                            "sSourceCD»sDescript»sDescCode");
                if (loValue != null){
                    poData.setSourceCd((String) loValue.get("sSourceCD"));
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
            if(fsValue.trim().equalsIgnoreCase((String)getBranch().getMaster(fsFieldNm))){
                setMessage("The same Branch Name!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sBranchCd"))
        {
            if(fsValue.trim().equalsIgnoreCase((String)(String)getBranch().getMaster(fsFieldNm))){
                setMessage("The same Branch Code!");
                return false;
            }
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
            }

            System.out.println("After Execute");

        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
            if(pbWithParnt)
                MiscUtil.close(loCon);
        }

        return lbHasRec;
    }
   
    public XMGCard getGCard(){
        if(poGCard == null)
            poGCard = new XMGCard(poGRider, psBranchCd, true);

        poGCard.openRecord(poData.getGCardNo());
        return poGCard;
    }

    public XMGCPointBasis getSource(){
        if(poSource == null)
            poSource = new XMGCPointBasis(poGRider, psBranchCd, true);

        poSource.openRecord(poData.getSourceCd());
        return poSource;
    }

    public XMBranch getBranch(){
        if(poBranch == null)
            poBranch = new XMBranch(poGRider, psBranchCd, true);

        poBranch.openRecord(psBranchCd);
        return poBranch;
    }

    public void setHasBonus(boolean hasBonus) {
        pbHasBonus = hasBonus;
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

    private void setPoints(String code, double amt, boolean hasbonus){
        System.out.println("setPoints(" + code + "," + amt + ")");
        //if(code == null || code.isEmpty() || amt == 0)
        if(code == null || code.isEmpty())
            poData.setValue("nPointsxx", 0.00);
        else{
            long point = 0;
         
            System.out.println("setPoints: get the validator");
            String validator = poGRider.getValidator("GCardPoint", code);
            if(validator == null){
                setMessage("Invalid validator detected...\n" +
                        "Verification of transaction failed!");
                MsgBox.showOk(getMessage());
                return;
            }

            System.out.println("setPoints: load validator " + validator);
            GCardPoint valid = (GCardPoint) MiscUtil.createInstance(validator);
            valid.setGRider(poGRider);
            valid.setData(poData);
            valid.checkSource();
         
            System.out.println("setPoints: check return value from " + validator);
            if(valid.getPoints() > 0){
                point = valid.getPoints();
                if(isInGCOnline()){
                    System.out.println("setPoints: With Online...");
                    point = 0;
                }
                else if(isInGCOffline()){
                    System.out.println("setPoints: With Offline...");
                    point = 0;
                }              
            }
            else{
                if(valid.getMessage().length() > 0)
                    MsgBox.showOk(valid.getMessage());
            }
         
            poData.setValue("nPointsxx", point + 0.00);
            
            //mac 2019.07.15
            //  get the transaction amount from validator
            poData.setValue("nTranAmtx", valid.getTotalAmount());
        }
    }
    
    private boolean isEmployee(){
        boolean bGC = false;
        
        Connection loCon = poGRider.getConnection();
        ResultSet loRS = null;
      
        String lsSQL = "SELECT sEmployID FROM Employee_Master001" + 
                        " WHERE sEmployID = " + SQLUtil.toSQL(System.getProperty("app.client.id")) +
                            " AND cRecdStat = '1'";
        
        try {
            System.out.println("Before Execute");

            System.out.println(lsSQL);
            loRS = loCon.createStatement().executeQuery(lsSQL);

            if(loRS.next()) {
                setMessage("GCard of employees are not allowed to earn points.");
                bGC = true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
            bGC = true;
        }
        finally{
            MiscUtil.close(loRS);
        }
        
        return bGC;
    }

    private boolean isInGCOffline(){
        boolean bGC = false;

        Connection loCon = poGRider.getConnection();
        ResultSet loRS = null;
      
        String lsSQL = "SELECT IFNULL(SUM(nPointsxx), 0) nPointsxx"  + 
                        " FROM G_Card_Detail_Offline" + 
                        " WHERE sTransNox LIKE " + SQLUtil.toSQL(poData.getTransNo().substring(0, 4) + "%") +
                          " AND sTransNox <> " + SQLUtil.toSQL(poData.getTransNo()) +
                          " AND dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(poData.getTranDate(), "yyyy-MM-dd")) + 
                          " AND sSourceNo = " + SQLUtil.toSQL(poData.getSourceNo()) + 
                          " AND sSourceCD = " + SQLUtil.toSQL(poData.getSourceCd()) +  
                          " AND cTranStat IN ('0', '1', '2')";
        try {
            System.out.println("Before Execute");

            System.out.println(lsSQL);
            loRS = loCon.createStatement().executeQuery(lsSQL);

            if(loRS.next()){
                if(loRS.getDouble("nPointsxx") > 0 )
                    bGC = true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
            bGC = true;
         }
        finally{
            MiscUtil.close(loRS);
        }
      
        return bGC;
    }

    private boolean isInGCOnline(){
        boolean bGC = false;

        Connection loCon = poGRider.getConnection();
        ResultSet loRS = null;
      
        String lsSQL = "SELECT IFNULL(SUM(nPointsxx), 0) nPointsxx"  + 
                        " FROM G_Card_Detail" + 
                        " WHERE sTransNox LIKE " + SQLUtil.toSQL(poData.getTransNo().substring(0, 4) + "%") +
                          " AND dTransact = " + SQLUtil.toSQL(SQLUtil.dateFormat(poData.getTranDate(), "yyyy-MM-dd")) + 
                          " AND sSourceNo = " + SQLUtil.toSQL(poData.getSourceNo()) + 
                          " AND sSourceCD = " + SQLUtil.toSQL(poData.getSourceCd());
        try {
            System.out.println("Before Execute");

            System.out.println(lsSQL);
            loRS = loCon.createStatement().executeQuery(lsSQL);

            if(loRS.next()){
                System.out.println(loRS.getDouble("nPointsxx"));
                if(loRS.getDouble("nPointsxx") > 0)
                    bGC = true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
            bGC = true;
        }
        finally{
            MiscUtil.close(loRS);
        }
      
        return bGC;
    }
   
    public void setWhite(int white) {
        pnWhite = white;
    }

    public void setYellow(int yellow) {
        pnYellow = yellow;
    } 
   
    private String getSQL_Master(){        
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.sSourceNo" +
                    ", e.sDescript" +
                    ", a.dTransact" +
                    ", f.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", f.cDigitalx" +
                    ", f.sClientID" +
                    ", f.sGCardNox" +
                " FROM G_Card_Detail_Offline a" +
                            " LEFT JOIN G_Card_Points_Basis e ON a.sSourceCd = e.sSourceCd" +
                        ", G_Card_Master f" +
                        ", Client_Master b" +
                            " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                            " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sGCardNox = f.sGCardNox" +
                    " AND f.sClientID = b.sClientID";
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
    }

    private String getSQL_Client(){
        return "SELECT" +
                    "  a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                    ", a.cCardStat" + 
                    ", b.sClientID" + 
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat <= " + SQLUtil.toSQL(GCardStatus.ACTIVATED);
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
    }

    private String getSQL_Source(){
        return "SELECT" +
                     "  sSourceCD" +
                     ", sDescCode" +
                     ", sDescript" +
                " FROM G_Card_Points_Basis" +
                " WHERE cSignedUp = '0'" +
                    " AND cRecdStat = '1'";
    }

    private String getSQL_Branch(){
        return "SELECT" +
                    "  sBranchCd" +
                    ", sBranchNm" +
                " FROM Branch" +
                " WHERE cRecdStat = '1'";
    }
    
    public Object getDeviceInfo(){
        if(poGCDevice == null){
            return null;
        }

        return poGCDevice;
    }
    
    public boolean connectCard(){
        if (null == GCardDeviceFactory.DeviceTypeID(System.getProperty("app.device.type"))){
            poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.SMARTCARD);
        } else
            switch (GCardDeviceFactory.DeviceTypeID(System.getProperty("app.device.type"))) {
                case SMARTCARD:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.SMARTCARD);
                    break;
                case NONE:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.NONE);
                    poGCDevice.setCardNo(System.getProperty("app.card.no"));
                    break;
                case QRCODE:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.QRCODE);
                    poGCDevice.setCardNo(System.getProperty("app.card.no"));
                    break;
                default:
                    setMessage("Invalid Device Type.");
                    return false;
        }
        
        poGCDevice.setGRider(poGRider);
        
        if (poGCDevice.read()){
            System.setProperty("app.gcard.no", (String) poGCDevice.getCardInfo("sGCardNox"));
            System.setProperty("app.gcard.holder", (String) poGCDevice.getCardInfo("sCompnyNm"));
            System.setProperty("app.card.no", (String) poGCDevice.getCardInfo("sCardNmbr"));
            System.setProperty("app.gcard.mobile", (String) poGCDevice.getCardInfo("sMobileNo"));
            System.setProperty("app.device.data", poGCDevice.getCardInfo().toJSONString());
            System.setProperty("app.card.connected", "1");
            System.setProperty("app.gcard.online", String.valueOf(poGCDevice.getCardInfo("bIsOnline")));
            System.setProperty("app.client.id", (String) poGCDevice.getCardInfo("sClientID"));
            
            if (!"4".equals((String) poGCDevice.getCardInfo("cCardStat"))){
                setMessage("G-Card is not activated. Please activate the card to continue...");
                return false;
            }

            poData.setGCardNo((String)poGCDevice.getCardInfo("sGCardNox"));
            return true;
        } else {
            System.setProperty("app.card.connected", "0");
            setMessage(poGCDevice.getMessage());
            return false;
        }        
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}

    private static LogWrapper logwrapr = new LogWrapper("XMGCOffPoints", "temp/XMGCOffPoints.log");

    private XMGCard poGCard = null;
    private XMGCPointBasis poSource = null;
    private XMBranch poBranch = null;
    private GCardDevice poGCDevice;

    private UnitGCardDetailOffline poData;
    private GCOffPoints poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
    private boolean pbWithParnt = false;

    private boolean pbHasBonus = false;
    private int pnWhite = 0;
    private int pnYellow = 0;   

    private String psReferNox;
    private boolean pbIsInLR;
    private boolean pbIsInGC;
}
