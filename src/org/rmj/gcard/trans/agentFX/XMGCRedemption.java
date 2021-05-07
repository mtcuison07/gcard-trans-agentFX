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
import java.util.Calendar;
import java.util.Date;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.GCardStatus;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.replication.utility.LogWrapper;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.gcard.trans.GCRedemption;
import org.rmj.gcard.device.ui.GCardDevice;
import org.rmj.gcard.device.ui.GCardDeviceFactory;
import org.rmj.gcard.formFX.ServiceCoupon;
import org.rmj.gcard.service.GCRestAPI;
import org.rmj.integsys.pojo.UnitGCardRedemption;
import org.rmj.parameters.agent.XMBranch;

/**
 * @author kalyptus
 */
public class XMGCRedemption {
    public XMGCRedemption(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCRedemption();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;
            this.pnSupplmnt = (float)0.00;
            this.psORNoxxxx = "";
            
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
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){

            if(fnCol == poData.getColumn("dTransact")){
                if(foData instanceof Date){
                    poData.setValue(fnCol, foData);
                }   
                else
                    poData.setValue(fnCol, null);
            }//end: if(fnCol == poData.getColumn("nTranAmtx"))
            else if(fnCol == poData.getColumn("sPromCode")){
                String promo = (String) foData;
                if ((!promo.isEmpty()) && !promo.equalsIgnoreCase((String)(this.getPromo().getMaster("sPromCode")))){
                    poData.setValue("sPromCode", foData);
                    poData.setValue("nPointsxx", (Double)(this.getPromo().getMaster("nPointsxx")));
                }     
            }//end: if(fnCol == poData.getColumn("nQuantity"))
            else{
                poData.setValue(fnCol, foData);
            }//end: if(!(fnCol == poData.getColumn("sTransNox") - else
            }//end: if(!(fnCol == poData.getColumn("sTransNox") ... etc
        }//end: if(pnEditMode != EditMode.UNKNOWN && poCtrl != null)
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

    /*
    public void setORNo(String fsORNo){
        psORNoxxxx = fsORNo;
    }

    public void setCashAmt(float fnCash){
        pnSupplmnt = fnCash;
    }
    */
   
    public boolean newTransaction() {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCardRedemption) poCtrl.newTransaction();

        if(poData == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poGCard = null;
            poBranch = null;

            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean loadTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCardRedemption) poCtrl.loadTransaction(fsTransNox);

        if(poData.getTransNo() == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poGCard = null;
            poBranch = null;

            pnEditMode = EditMode.READY;
            return true;
        }
    }
   
    private String getSQ_Motorcycle(){
        return "SELECT" +
                    "  a.sSerialID" +
                    ", a.sEngineNo" +
                    ", a.sFrameNox" +
                    ", b.sCompnyNm" +
                    ", d.sBrandNme" +
                    ", c.sModelNme" +
                    ", b.sClientID" +
                " FROM MC_Serial a" +
                    " LEFT JOIN MC_Model c" +
                        " ON a.sModelIDx = c.sModelIDx" +
                    " LEFT JOIN Brand d" +
                        " ON c.sBrandIDx = d.sBrandIDx" +
                    ", Client_Master b" +
                " WHERE a.sClientID = b.sClientID";
    }

    public boolean saveUpdate() {
        if(poCtrl == null){
            return false;
        }//end: if(poCtrl == null)
        else if(pnEditMode == EditMode.UNKNOWN){
            return false;
        }//end: if(poCtrl == null) - else if(pnEditMode == EditMode.UNKNOWN)
        else{
            boolean lbResult = true;
         
            //kalyptus - 2018.05.08 09:57am
            //Implement club members validation
            if(((String)this.getGCard().getMaster("cIndvlPts")).contentEquals("0")){
                if(((String)this.getGCard().getMaster("cMainGrpx")).contentEquals("0")){
                    setMessage("Club members can redeem thru the Mother GCard Only!");               
                    return false;
                }
            }
         
            //check again if allow update
            if("M00114000025»M00114000026".contains(poData.getPromoID())){
                if(!isAllowRepeat()){            
                    return false; 
                }    
            }

            //kalyptus - 2016.01.19 01:17pm
            //Get engine of MC to extend...
            if("M00115000027".contains(poData.getPromoID())){
                JSONObject loJSon = new JSONObject();
                loJSon.put("sSourceNo", "");

                //Original Code:
                //  loJSon.put("sSerialID", "");
                ShowMessageFX.Information(null, this.getClass().getSimpleName(), "Please select MOTORCYCLE on next dialog...");

                ResultSet loRS = poGRider.executeQuery(
                                    MiscUtil.addCondition(getSQ_Motorcycle(), 
                                                            "d.sClientID = " + (String) poGCDevice.getCardInfo("sClientID")));
                
                try {
                    if (!loRS.next()){
                        setMessage("No record found for this customer...");                    
                        return false;
                    }

                    JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Brand»Model»Engine No.»Frame No.", 
                                                                "sBrandNme»sModelNme»sEngineNo»sFrameNox");

                    if (loValue == null){
                        setMessage("No motorcycle selected...");
                        return false;
                    }

                    poCtrl.setSerial((String) loValue.get("sSerialID"));                  
                    loJSon.put("sEngineNo", (String) loValue.get("sEngineNo"));
                    loJSon.put("sFrameNox", (String) loValue.get("sFrameNox"));
                    loJSon.put("sModelNme", (String) loValue.get("sModelNme"));
                } catch (SQLException ex) {
                    setMessage(ex.getMessage());
                    return false;
                }                

                loJSon.put("nPointsxx", 0);
                loJSon.put("nYellowxx", 0);
                loJSon.put("nWhitexxx", 3);

                ServiceCoupon loserv = new ServiceCoupon();
                loserv.setData(loJSon);
                loserv.showGUI();
            
                if(!loserv.isOkey()){
                    setMessage("Entry was cancelled...");
                   return false;
                }

                /*
                if(!loserv.isOkey()){
                    //TODO: Display the message here...
                   MsgBox.showOk("Entry was cancelled...");
                   return false;
                }
                else{
                    String lsSerialID = (String)loserv.getData().get("sSerialID");
                    if(lsSerialID.isEmpty()){
                        MsgBox.showOk("Please enter an Engine No...");
                        return false;
                    }
                    poCtrl.setSerial(lsSerialID);
                }*/
            }   

            if(null == poGCDevice.UIDeviceType()){
                poData.setOTPassword("");
            } else switch (poGCDevice.UIDeviceType()) {
                case QRCODE:
                    poData.setOTPassword(String.format("%06d", MiscUtil.getRandom(999999)));
                    break;
                case NONE:
                    //mac 2020-06-18
                    //check if server is active
                    if (!CommonUtils.isURLOnline(CommonUtils.getConfiguration(poGRider, "WebSvr"))){
                        setMessage("Main office server cannot be reached and device use is a NO/NON-CHIP CARD. Please encode this entry as OFFLINE POINTS ENTRY.");
                        return false;
                    }
                    
                    //mac 2020-06-09
                    //  added no chip card for otp generation.
                    poData.setOTPassword(String.format("%06d", MiscUtil.getRandom(999999)));
                    break;
                default:
                    poData.setOTPassword("");
                    break;
            }

            //mac 2019.06.24
            long lnPoints;
            if (Double.valueOf(poGCDevice.getCardInfo("nAvlPoint").toString()).longValue() >
                    Double.valueOf(poGCDevice.getCardInfo("nDevPoint").toString()).longValue()){
                lnPoints = Double.valueOf(poGCDevice.getCardInfo("nDevPoint").toString()).longValue();
            } else
                lnPoints = Double.valueOf(poGCDevice.getCardInfo("nAvlPoint").toString()).longValue();

            long lnPromo = Double.valueOf(this.getPromo().getMaster("nPointsxx").toString()).longValue();

            if (lnPromo > lnPoints){
                setMessage("G-Card points available is below the redeemable point value...");
                return false;
            }
            
            //mac 2019.07.11
            //  cash supplement was not allowed by the management na...
            /*
            if (lnPromo > lnPoints){
                //cash supplement is needed; show cash supplement form
                JSONObject loCash = new JSONObject();
                loCash.put("dTransact", (Date) poGRider.getServerDate());
                loCash.put("sClientNm", (String) poGCDevice.getCardInfo("sCompnyNm"));
                loCash.put("sCardNmbr", (String) poGCDevice.getCardInfo("sCardNmbr"));
                loCash.put("nAvlPoint", lnPoints);
                loCash.put("nCashAmtx", (long) lnPromo - lnPoints);

                CashSupplement loSupplement = new CashSupplement();
                loSupplement.setData(loCash);

                loSupplement.showGUI();

                if (!loSupplement.isOkey()){
                    setMessage(loSupplement.getMessage());
                    return false;
                }

                loCash = new JSONObject();
                loCash = loSupplement.getData();

                psORNoxxxx = (String) loCash.get("sORNoxxxx");
                pnSupplmnt = lnPromo - lnPoints;

                if (lnPromo > lnPoints + pnSupplmnt){
                    setMessage("G-Card points and cash supplement is not enough to redeem...");
                    return false;
                }
            }
            */

            JSONObject poJson = new JSONObject();
            poJson.put("SOURCE", "REDEMPTION");
            poJson.put("sOTPasswd", (String)poData.getValue("sOTPasswd"));

            JSONArray poArr = new JSONArray();
            JSONObject poDtl = new JSONObject();
            poDtl.put("sTransNox", poData.getTransNo());
            poDtl.put("dTransact", poData.getTransactDate());
            poDtl.put("sSourceNo", poData.getSourceNo());
            poDtl.put("sSourceCD", (String)(this.getPromo().getMaster("sPromDesc"))); //sPromCode
            poDtl.put("nPointsxx", (Double) this.getPromo().getMaster("nPointsxx") - pnSupplmnt);
            
            poDtl.put("sSourcexx", poData.getSourceCd()); //source code
            poDtl.put("nTranAmtx", (Double) this.getPromo().getMaster("nPointsxx") - pnSupplmnt); //since this is a redemption, I copied the points value to tran amount
            
            poArr.add(poDtl);
            poJson.put("DETAIL", poArr);
            
            if (poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.SMARTCARD)
                poJson.put("nPointsxx", new Double((Double) this.getPromo().getMaster("nPointsxx") - pnSupplmnt).longValue());
            
            poGCDevice.setTransData(poJson);

            if(!poGCDevice.write()){
                setMessage(poGCDevice.getMessage());
                return false;
            }

            UnitGCardRedemption loResult=null;
            if(!pbWithParnt) poGRider.beginTrans();

            if(lbResult){
                if(poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.QRCODE){
                    JSONObject poDig = new JSONObject();
                    poDig.put("sIMEINoxx", (String)poGCDevice.getCardInfo("sIMEINoxx"));
                    poDig.put("sUserIDxx", (String)poGCDevice.getCardInfo("sUserIDxx"));
                    poDig.put("sMobileNo", (String)poGCDevice.getCardInfo("sMobileNo"));
                    poDig.put("dQRDateTm", String.valueOf(poGRider.getServerDate())); //(String)poGCDevice.getCardInfo("dQRDateTm")
                    poCtrl.setDigital(poDig);
                }
                else{
                    poCtrl.setDigital(null);
                }          

                //set OR and suplementary amount needed by the GC
                poCtrl.setORNo(psORNoxxxx);
                poCtrl.setCashAmt(pnSupplmnt);
                poCtrl.setClient((String)poGCard.getMaster("sClientID"));

                if(pnEditMode == EditMode.ADDNEW)
                    loResult = (UnitGCardRedemption) poCtrl.saveUpdate(poData, "");
                else
                    loResult = (UnitGCardRedemption) poCtrl.saveUpdate(poData, (String) poData.getValue(1));
            }//end: if(lbResult)

            if(loResult == null){
                if(!(poCtrl.getErrMsg() + poCtrl.getMessage()).isEmpty()){
                    setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());                
                } 

                logwrapr.severe("saveUpdate: Unable to save " + (String) poData.getGCardNo() + " with Trans No. " + (String) poData.getTransNo());

                if(!pbWithParnt) poGRider.rollbackTrans();
                return false;

            }//end: if(loResult == null)
            else{
                //kalyptus - 2019.10.22 10:23am
                //send the points update to the main server here...
                long points = new Double((Double)this.getPromo().getMaster("nPointsxx") - pnSupplmnt).longValue();
                //long points = (long) ((long)(this.getPromo().getMaster("nPointsxx")) - pnSupplmnt);
                JSONObject response = 
                        GCRestAPI.UpdatePoint(poGRider, 
                        (String) poGCDevice.getCardInfo("sCardNmbr"),
                        "REDEMPTION",
                        loResult.getTransNo(),
                        points);
                String result = (String) response.get("result");
                if(result.equalsIgnoreCase("success")){
                    String sql = "UPDATE " + loResult.getTable() + 
                                " SET cPointSnt = '1'" + 
                                " WHERE sTransNox = " + SQLUtil.toSQL(loResult.getTransNo());
                    poGRider.executeQuery(sql, loResult.getTable(), "", "");
                }

                pnEditMode = EditMode.READY;
                poData = loResult;

                if(!pbWithParnt) poGRider.commitTrans();
                
                setMessage("Transaction saved successfully...");
                return true;
            }//end: if(loResult == null) - else
        }//end: if(poCtrl == null) - else
    }

    public boolean deleteTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();
            boolean lbResult = poCtrl.deleteTransaction(fsTransNox);

            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();
             
             
            return lbResult;
        }
    }

    public boolean closeTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            boolean lbResult = poCtrl.closeTransaction(fsTransNox);
            if(!pbWithParnt) poGRider.beginTrans();

            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();

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
            boolean lbResult = poCtrl.postTransaction(fsTransNox);
            if(!pbWithParnt) poGRider.beginTrans();

            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();
             
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
            return false;
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
            return false;
        }
    }

    public boolean loadLastFromCard(GCardDeviceFactory.DeviceType cardtype){
        poGCDevice = GCardDeviceFactory.make(cardtype);
        if(!poGCDevice.read()){
            setMessage(poGCDevice.getMessage());
            return false;
        }

        String lsGCardNmbr = (String)poGCDevice.getCardInfo("sCardNmbr"); 
        XMGCard gcard = new XMGCard(poGRider, psBranchCd, true);
        if(!gcard.searchWithCondition("sCardNmbr" , lsGCardNmbr , "")){
            logwrapr.severe("loadLastFromCard: Unable to load last transaction " + lsGCardNmbr + "!");
            setMessage("Unable to load last transaction!");
            return false;
        }

        return loadTransaction((String)gcard.getMaster("sLastLine"));
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

    public boolean searchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }//end: if(fsValue.trim().length() == 0)

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
            }//end: if(pnEditMode != EditMode.UNKNOWN)
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sTransNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
        }// end: if(fsFieldNm.equalsIgnoreCase("sTransNox"))
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
        }//end: if(fsFieldNm.equalsIgnoreCase("sTransNox")) - else if(fsFieldNm.equalsIgnoreCase("sClientNm"))=
        else if(fsFieldNm.equalsIgnoreCase("sSourceNo")){
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
            }

            lsSQL = MiscUtil.addCondition(lsSQL, "a.sSourceNo LIKE " + SQLUtil.toSQL(fsValue));
        }//end: if(fsFieldNm.equalsIgnoreCase("sTransNox")) - else if(fsFieldNm.equalsIgnoreCase("sSourceNo"))

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
                                                                "Client Name»Address»Card Number»Date»Source No»Trans. No", 
                                                                "sClientNm»xAddressx»f.sCardNmbr»a.dTransact»a.sSourceNo»a.sTransNox");
              
                if (loValue != null){
                    lbHasRec = loadTransaction((String) loValue.get("sTransNox"));
                } else
                    setMessage("No record selected...");
            }

            System.out.println("After Execute");

        }//end: try {
        catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
            if(!pbWithParnt)
                MiscUtil.close(loCon);
        }

        return lbHasRec;
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
        else if(field.equalsIgnoreCase("sPromoIDx")){
            return searchPromo(field, value);
        }
        else if(field.equalsIgnoreCase("sPromCode")){
            return searchPromo(field, value);
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

    public boolean releaseCard(){
        if(!poGCDevice.release()){
            setMessage(poGCDevice.getMessage());
            return false;
        }
        return true;
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
        Connection loCon  = poGRider.getConnection();

        if(loCon == null){
            setMessage("Invalid connection!");
            return false;
        }

        boolean lbHasRec = false;
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            System.out.println("Before Execute");
            
            //mac 2020-07-21
            //validate selected card type
            if (!System.getProperty("app.device.type").isEmpty()){
                lsSQL = MiscUtil.addCondition(lsSQL, "a.cDigitalx = " + SQLUtil.toSQL(System.getProperty("app.device.type")));
            }

            loStmt = loCon.createStatement();
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);
            
            ClearGCardProperty();

            if(!loRS.next())
                poData.setGCardNo("");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Name»Address»Card Number»Activated", 
                                                                "sClientNm»xAddressx»sCardNmbr»dTransact");
              
                if (loValue != null){
                    System.setProperty("app.gcard.no", (String) loValue.get("sGCardNox"));
                    System.setProperty("app.card.no", (String) loValue.get("sCardNmbr"));
                    System.setProperty("app.gcard.holder", (String) loValue.get("sClientNm"));
                    System.setProperty("app.client.id", (String) loValue.get("sClientID"));
                    System.setProperty("app.device.type", (String) loValue.get("cDigitalx"));
                    
                    
                    poData.setGCardNo((String) loValue.get("sGCardNox"));
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

    private boolean searchPromo(String fsFieldNm, String fsValue){
        System.out.println("Inside searchPromo");
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
      
        String lsSQL = getSQL_Promo();
        if(fsFieldNm.equalsIgnoreCase("sPromoIDx")){
            if(fsValue.trim().equalsIgnoreCase((String)this.getPromo().getMaster("sTransNox"))){
                setMessage("The same Promo ID!");
                return false;
            } //if(fsValue.trim().equalsIgnoreCase((String)poParts.getMaster("sTransNox"))){

            lsSQL = MiscUtil.addCondition(lsSQL, "a.sTransNox" + " LIKE " + SQLUtil.toSQL(fsValue));
        } //if(fsFieldNm.equalsIgnoreCase("sPromoIDx")){
        else if(fsFieldNm.equalsIgnoreCase("sPromCode")){
            if(fsValue.trim().equalsIgnoreCase((String)this.getPromo().getMaster(fsFieldNm))){
                setMessage("The same promo code!");
                return false;
            } //if(fsValue.trim().equalsIgnoreCase((String)poParts.getMaster(fsFieldNm))){
        } //else if(fsFieldNm.equalsIgnoreCase("sPromCode")){
      
        //sSQL = MiscUtil.addCondition(lsSQL, "a.sPromCode" + " LIKE " + SQLUtil.toSQL(fsValue + "%"));
              
        if (poGCDevice == null) {
            setMessage("Please connect a card!");
            return false;
        }
        
        String lcCardType = (String) poGCDevice.getCardInfo("cCardType");

        String lsFilter = "a.dDateFrom <= " + SQLUtil.toSQL(poData.getTransactDate()) + 
                            " AND a.dDateThru >= " + SQLUtil.toSQL(poData.getTransactDate()) +
                            " AND a.sCardType LIKE " + SQLUtil.toSQL("%" + lcCardType + "%"); 
                      
        //Filter the type of transaction here accdg to the GCard Type
        lsSQL = MiscUtil.addCondition(lsSQL, lsFilter);

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
                setMessage("There are no promos available at this moment.");
                
                poData.setPromoID("");
                poData.setPoints(0.00);  
            }else{
                JSONObject loValue = showFXDialog.jsonSearch(poGRider, 
                                                            lsSQL, 
                                                            fsValue, 
                                                            "Promo Code»Description»Points", 
                                                            "sPromCode»sPromDesc»nPointsxx", 
                                                            "a.sPromCode»a.sPromDesc»a.nPointsxx", 0);
                
                if (loValue != null){
                    poData.setPromoID((String) loValue.get("sTransNox")); //loRS.getString("sTransNox")
                    poData.setPoints(Double.valueOf((String) loValue.get("nPointsxx"))); //loRS.getDouble("nPointsxx")
                    if("M00114000025»M00114000026".contains((String) loValue.get("sTransNox"))){ //loRS.getString("sTransNox")
                        if(!isAllowRepeat()){
                            poData.setPromoID("");
                            poData.setPoints(0.00);
                        } else
                            lbHasRec = true;
                    }else{
                        lbHasRec = true;
                    }
                } else
                    setMessage("No record selected...");
            }

            System.out.println("After Execute");
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        } finally{
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
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same Branch Name!");                
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sBranchCd"))
        {
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
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
        }

        return lbHasRec;
    }

    public boolean isAllowRepeat(){
        System.out.println("inside isAllowRepeat");

        Connection loCon = poGRider.getConnection();

        if(loCon == null){
            setMessage("Invalid connection!");
            return false;
        }

        boolean lbIsOK;
        Statement loStmt = null;
        ResultSet loRS = null;

        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(poData.getTransactDate());
        cal1.set(Calendar.DATE, cal1.getActualMinimum(Calendar.DATE));

        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(poData.getTransactDate());
        cal2.set(Calendar.DATE, cal2.getActualMaximum(Calendar.DATE));

        String lsSQL;

        lsSQL = "SELECT *" + 
                " FROM G_Card_Redemption" +
                " WHERE sGCardNox = " + SQLUtil.toSQL(poData.getGCardNo()) + 
                    " AND sPromoIDx = " + SQLUtil.toSQL(poData.getPromoID()) +
                    " AND dTransact BETWEEN " + SQLUtil.toSQL(cal1.getTime()) + " AND " + SQLUtil.toSQL(cal2.getTime()); 

        System.out.println(lsSQL);
        try {
            System.out.println("Before Execute");

            loStmt = loCon.createStatement();
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);

            int lnCtr = 0; 
            lbIsOK = true;
         
            while(loRS.next()){
                System.out.println(SQLUtil.dateFormat(loRS.getDate("dTransact"), "yyyy-MM-dd") + "»" + SQLUtil.dateFormat(poData.getTransactDate(),"yyyy-MM-dd"));

                if(SQLUtil.dateFormat(loRS.getDate("dTransact"), "yyyy-MM-dd").equals(SQLUtil.dateFormat(poData.getTransactDate(),"yyyy-MM-dd"))){
                    if(loRS.getDouble("nPointsxx") > 0)
                        lnCtr++;
                    else
                        lnCtr--;
                }
                else{
                    setMessage("Redemption for this Reward is once per month only!");
                    lbIsOK = false;
                }    
            }

            if(lbIsOK){
                if(lnCtr > 1){
                    setMessage("Maximum redemption is 300 only!");                 
                    lbIsOK = false;
                }    
            }
         
            System.out.println("After Execute");
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
            lbIsOK = false;
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
       
        return lbIsOK;
    }
   
    public XMGCard getGCard(){
        if(poGCard == null)
            poGCard = new XMGCard(poGRider, psBranchCd, true);

        poGCard.openRecord(poData.getGCardNo());
        return poGCard;
    }

    public XMBranch getBranch(){
        if(poBranch == null)
            poBranch = new XMBranch(poGRider, psBranchCd, true);

        poBranch.openRecord(psBranchCd);
        return poBranch;
    }
    
    public Object getDeviceInfo(){
        if(poGCDevice == null){
            return null;
        }

        return poGCDevice;
    }

    public XMGCRedemptionPromo getPromo(){
        if(poParts == null)
            poParts = new XMGCRedemptionPromo(poGRider, psBranchCd, true );

        poParts.loadTransaction(poData.getPromoID());
        return poParts;
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

    private String getSQL_Master(){
       return "SELECT" +
                    "  a.sTransNox" +
                    ", a.sSourceNo" +
                    ", a.dTransact" +
                    ", f.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", f.sGCardNox" +
                    ", f.cDigitalx" +
                    ", f.sClientID" +
                " FROM G_Card_Redemption a" +
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
                    ", a.cDigitalx" +
                    ", a.sClientID" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = " + SQLUtil.toSQL(GCardStatus.ACTIVATED);
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')),b.sMiddName)
    }

    private String getSQL_Promo(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.sPromCode" +
                    ", a.sPromDesc" +
                    ", a.nPointsxx" +
                " FROM G_Card_Promo_Master a" + 
                " WHERE a.cTranStat <> '3'";
    }

    private String getSQL_Branch(){
        return "SELECT" +
                    "  sBranchCd" +
                    ", sBranchNm" +
                " FROM Branch" +
                " WHERE cRecdStat = '1'";
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

    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
   
    private XMGCard poGCard = null;
    private XMBranch poBranch = null;
    private XMGCRedemptionPromo poParts = null;

    private static LogWrapper logwrapr = new LogWrapper("XMGCRedemption", "temp/XMGCRedemption.log");
   
    private UnitGCardRedemption poData;
    private GCRedemption poCtrl;
    private GRider poGRider;
    private GCardDevice poGCDevice;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
    private boolean pbWithParnt = false;
   
    private String psORNoxxxx;
    private float pnSupplmnt; 
}
