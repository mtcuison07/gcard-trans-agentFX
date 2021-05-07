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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.RecordStatus;
import org.rmj.gcard.device.ui.GCardDevice;
import org.rmj.gcard.device.ui.GCardDeviceFactory;
import org.rmj.gcard.formFX.ServiceCoupon;
import org.rmj.gcard.service.GCRestAPI;
import org.rmj.gcard.trans.GCard;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author sayso
 */
public class XMGCPreOrder {    
    public XMGCPreOrder(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            pnEditMode = EditMode.UNKNOWN;
        }
    }
    
    //mac 2020.03.17
    public boolean connectCard(){
        //since we are connected to a device, reset the system variables
        System.setProperty("app.card.connected", "");
        System.setProperty("app.gcard.no", "");
        System.setProperty("app.gcard.holder", "");
        System.setProperty("app.card.no", "");
        System.setProperty("app.device.type", "");
        System.setProperty("app.device.data", "");
        System.setProperty("app.client.id", "");
        System.setProperty("app.gcard.online", "");
        
        //Preorder is for QRCode Reader only...
        poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.QRCODE);
        poGCDevice.setGRider(poGRider);
        
        if(!poGCDevice.read()){
            psMessage = poGCDevice.getMessage();
            return false;
        }
        
        //check if source of QRCode is not from PREORDER
        String source = (String) poGCDevice.getCardInfo("sTransNox");
        if(source.isEmpty()){
            psMessage = "Transaction source is empty.";
            return false;
        }

        //check local record...
        String lsSQL = "SELECT *" + 
                      " FROM G_Card_Order_Master" + 
                      " WHERE sTransNox = " + SQLUtil.toSQL(source);
        ResultSet rsOrder = poGRider.executeQuery(lsSQL);
        String result;
        String stat;
        try {
            if(rsOrder.next()){
                stat = rsOrder.getString("cTranStat");
                if(stat.equalsIgnoreCase("2")){
                    psMessage = "Order was already redeem!";
                    return false;
                }
                else if(stat.equalsIgnoreCase("3")){
                    psMessage = "Order was already cancelled!";
                    return false;
                }
                
                if (!rsOrder.getString("sBranchCd").equalsIgnoreCase(psBranchCd)){
                    psMessage = "Preferred branch is not your branch.";
                    return false;
                }
            }
        } catch (SQLException ex) {
            psMessage = ex.getMessage();
            return false;
        }
        
        //request information of preorder from the server...
        JSONObject poJson = GCRestAPI.RequestOrderInfo(poGRider, source);
        
        //check error in our request
        result = (String) poJson.get("result");
        if(result.equalsIgnoreCase("error")){
            JSONObject error = (JSONObject) poJson.get("error");
            psMessage = (String) error.get("message");
            return false;
        }
        
        //check if status is okey...
        stat = (String) poJson.get("cTranStat");
        if(stat.equalsIgnoreCase("2")){
            psMessage = "Order was already redeem!";
            return false;
        }
        else if(stat.equalsIgnoreCase("3")){
            psMessage = "Order was already cancelled!";
            return false;
        }
        
        Calendar qrcal = Calendar.getInstance();
        poData = new JSONObject();
        poData.put("sTransNox", poJson.get("sTransNox"));
        poData.put("dPickupxx", SQLUtil.dateFormat(qrcal.getTime(), SQLUtil.FORMAT_TIMESTAMP));
        poData.put("sGCardNox", poJson.get("sGCardNox"));
        //poData.put("sPromoIDx", poJson.get("sPromoIDx"));
        //poData.put("nItemQtyx", poJson.get("nItemQtyx"));
        poData.put("nPointsxx", poJson.get("nPointsxx"));
        poData.put("sBranchCd", poJson.get("sBranchCd"));
        poData.put("sReferNox", poJson.get("sReferNox"));
        poData.put("dPlacOrdr", poJson.get("dPlacOrdr"));
        
        //new
        poData.put("cPlcOrder", poJson.get("cPlcOrder"));
        poData.put("sRemarksx", poJson.get("sRemarksx"));

        //poData.put("sRemarksx", "");
        poData.put("sApprCode", "");
        poData.put("sOTPasswd", "");
        poData.put("cPointSnt", "0");
        
        poData.put("cTranStat", poJson.get("cTranStat"));
        
        //put to json array
        poDetail = new ArrayList<>();
        JSONArray jsonArray = (JSONArray) poJson.get("detail");
        for(int lnCtr = 0; lnCtr <= jsonArray.size()-1; lnCtr++){
            JSONObject jsonObject = (JSONObject) jsonArray.get(lnCtr);
            poDetail.add(jsonObject);
        }

        pnEditMode = EditMode.READY;
        return true;
    }

    /*
    public boolean connectCard(){
        //Preorder is for QRCode Reader only...
        poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.QRCODE);
        poGCDevice.setGRider(poGRider);
        
        if(!poGCDevice.read()){
            psMessage = poGCDevice.getMessage();
            return false;
        }
        
        //check if source of QRCode is not from PREORDER
        String source = (String) poGCDevice.getCardInfo("sTransNox");
        if(source.isEmpty()){
            psMessage = "Transaction source is empty.";
            return false;
        }

        //check local record...
        String lsSQL = "SELECT *" + 
                      " FROM G_Card_Order_Master" + 
                      " WHERE sTransNox = " + SQLUtil.toSQL(source);
        ResultSet rsOrder = poGRider.executeQuery(lsSQL);
        String result;
        String stat;
        try {
            if(rsOrder.next()){
                stat = rsOrder.getString("cTranStat");
                if(stat.equalsIgnoreCase("2")){
                    psMessage = "Order was already redeem!";
                    return false;
                }
                else if(stat.equalsIgnoreCase("3")){
                    psMessage = "Order was already cancelled!";
                    return false;
                }
                
                if (!rsOrder.getString("sBranchCd").equalsIgnoreCase(psBranchCd)){
                    psMessage = "Preferred branch is not your branch.";
                    return false;
                }
            }
        } catch (SQLException ex) {
            psMessage = ex.getMessage();
            return false;
        }
        
        //request information of preorder from the server...
        JSONObject poJson = GCRestAPI.RequestOrderInfo(poGRider, source);
        
        //check error in our request
        result = (String) poJson.get("result");
        if(result.equalsIgnoreCase("error")){
            JSONObject error = (JSONObject) poJson.get("error");
            psMessage = (String) error.get("message");
            return false;
        }
        
        //check if status is okey...
        stat = (String) poJson.get("cTranStat");
        if(stat.equalsIgnoreCase("2")){
            psMessage = "Order was already redeem!";
            return false;
        }
        else if(stat.equalsIgnoreCase("3")){
            psMessage = "Order was already cancelled!";
            return false;
        }
        
        Calendar qrcal = Calendar.getInstance();
        poData = new JSONObject();
        poData.put("sTransNox", poJson.get("sTransNox"));
        poData.put("dPickupxx", SQLUtil.dateFormat(qrcal.getTime(), SQLUtil.FORMAT_TIMESTAMP));
        poData.put("sGCardNox", poJson.get("sGCardNox"));
        //poData.put("sPromoIDx", poJson.get("sPromoIDx"));
        //poData.put("nItemQtyx", poJson.get("nItemQtyx"));
        poData.put("nPointsxx", poJson.get("nPointsxx"));
        poData.put("sBranchCd", poJson.get("sBranchCd"));
        poData.put("sReferNox", poJson.get("sReferNox"));
        poData.put("dPlacOrdr", poJson.get("dPlacOrdr"));
        
        //new
        poData.put("cPlcOrder", poJson.get("cPlcOrder"));
        poData.put("sRemarksx", poJson.get("sRemarksx"));

        //poData.put("sRemarksx", "");
        poData.put("sApprCode", "");
        poData.put("sOTPasswd", "");
        poData.put("cPointSnt", "0");
        
        poData.put("cTranStat", poJson.get("cTranStat"));
        
        //put to json array
        poDetail = new ArrayList<>();
        JSONArray jsonArray = (JSONArray) poJson.get("detail");
        for(int lnCtr = 0; lnCtr <= jsonArray.size()-1; lnCtr++){
            JSONObject jsonObject = (JSONObject) jsonArray.get(lnCtr);
            poDetail.add(jsonObject);
        }

        pnEditMode = EditMode.READY;
        return true;
    }*/

    public boolean saveUpdate() {
        if(poData == null){
            psMessage = "No data to process...";
            return false;
        }//end: if(poCtrl == null)
        else if(pnEditMode == EditMode.UNKNOWN){
            psMessage = "Invalid edit mode.";
            return false;
        }//end: if(poCtrl == null) - else if(pnEditMode == EditMode.UNKNOWN)
        else{
            if (poData.get("cPlcOrder").toString().equals("1")){
                psMessage = "Order was still on process.";
                return false;
            } else if (poData.get("cPlcOrder").toString().equals("2")){
                psMessage = "Order was still on transit.";
                return false;
            }
            
            boolean lbResult = true;

            //kalyptus - 2018.05.08 09:57am
            //Implement club members validation
            String cIndvlPts = (String) poGCDevice.getCardInfo("cIndvlPts");
            String cMainGrpx = (String) poGCDevice.getCardInfo("cMainGrpx");
            if(cIndvlPts.contentEquals("0")){
                if(cMainGrpx.contentEquals("0")){
                    psMessage = "Club members can redeem thru the Mother GCard Only!";
                    return false;
                }
            }
         
            //mac 2019.07.15
            //  get the promoid on the detail          
            String promoid = "";
            String lsSerialID = "";
            JSONObject loJSON;
            for (int x = 0; x < poDetail.size(); x++){
                loJSON = (JSONObject) poDetail.get(x);
                promoid = (String)loJSON.get("sPromoIDx");
                
                //mac 2019.07.15
                //nilipat ko ito papunta sa loob ng loop, dati nasa labas lang
                //kalyptus - 2016.01.19 01:17pm
                //Get engine of MC to extend...
                if("M00115000027".contains(promoid)){
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

                        lsSerialID = (String) loValue.get("sSerialID");                    
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

                    //Original code:
                    /*
                    if(!loserv.isOkey()){
                        psMessage = "Entry was cancelled...";
                        return false;
                    }
                    else{
                        lsSerialID = (String)loserv.getData().get("sSerialID");
                        if(lsSerialID.isEmpty()){
                            psMessage = "Please enter an Engine No...";
                            return false;
                        }
                    }
                    */
                }
                
                //END OF: nilipat ko ito papunta sa loob ng loop, dati nasa labas lang
            }
            
            if(poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.QRCODE){
                poData.put("sOTPasswd", String.format("%06d", MiscUtil.getRandom(999999)));
            }
            else{
                poData.put("sOTPasswd", "");
            }

            JSONObject poJson = new JSONObject();
            poJson.put("SOURCE", "PREORDER");
            poJson.put("sOTPasswd", (String)poData.get("sOTPasswd"));

            JSONArray poArr = new JSONArray();
            JSONObject poDtl = new JSONObject();
            poDtl.put("sTransNox", poData.get("sTransNox"));
            poDtl.put("dTransact", poData.get("dPickupxx"));
            poDtl.put("nPointsxx", Double.valueOf((String) poData.get("nPointsxx")));
            poDtl.put("sSourceNo", poData.get("sReferNox"));
            poDtl.put("sSourceCD", "");
            //poDtl.put("sSourceNo", "");
            //poDtl.put("sSourceCD", (String)(this.getPromo().getMaster("sPromDesc"))); //sPromCode

            poArr.add(poDtl);
            poJson.put("DETAIL", poArr);
            poGCDevice.setTransData(poJson);

            if(!poGCDevice.write()){
                psMessage = poGCDevice.getMessage();
                return false;
            }
            
            //Check local database again...
            String source = (String)poData.get("sTransNox");
            String lsSQL = "SELECT *" + 
                          " FROM G_Card_Order_Master" + 
                          " WHERE sTransNox = " + SQLUtil.toSQL(source);
            ResultSet rsOrder = poGRider.executeQuery(lsSQL);
            String stat;
            try {
                if(!rsOrder.next()){
                    stat = rsOrder.getString("cTranStat");
                    if(stat.equalsIgnoreCase("2")){
                        psMessage = "Order was already redeem!";
                        return false;
                    }
                    else if(stat.equalsIgnoreCase("3")){
                        psMessage = "Order was already cancelled!";
                        return false;
                    }
                }
            } catch (SQLException ex) {
                psMessage = ex.getMessage();
                return false;
            }
            
            for (int x = 0; x < poDetail.size(); x++){
                //mac 2019.07.15
                //  iloop ko ulit
                loJSON = (JSONObject) poDetail.get(x);
                promoid = (String)loJSON.get("sPromoIDx");
                
                if(!pbWithParnt) poGRider.beginTrans();

                if(promoid.equals("M00114000023")){
                    if(!issue_petron_value_card()){
                        if(!pbWithParnt){
                            poGRider.rollbackTrans();
                        }
                        psMessage = "Unable to issue Petron Value Card!";
                        return false;              
                    }
                }
                else if(promoid.equals("M00115000027")){
                    /*
                     * Kalyptus - 2016.05.30 02:25pm
                     *    - FSEP Extension is immediated tagged as active
                     */
                    String lsService = "INSERT INTO MC_Serial_Service_Extension(sSerialID, sGCardNox, nYellowxx, nWhitexxx, dTransact, cRecdStat)" +
                                         " VALUES( " + SQLUtil.toSQL(lsSerialID) +
                                                ", " + SQLUtil.toSQL((String) poData.get("sGCardNox")) +                    
                                                ", " + SQLUtil.toSQL(0) +
                                                ", " + SQLUtil.toSQL(3) +
                                                ", " + SQLUtil.toSQL(SQLUtil.dateFormat((Date) poData.get("dPickupxx"), "yyyy-MM-dd")) +
                                                ", " + SQLUtil.toSQL(RecordStatus.ACTIVE) + ")";                  

                    poGRider.executeQuery(lsService, "MC_Serial_Service_Extension", "", "");

                    if(!poGRider.getErrMsg().isEmpty()){
                        psMessage = poGRider.getErrMsg();
                        if(!pbWithParnt){
                              poGRider.rollbackTrans();
                        }
                        return false;         
                    }
                }

                if(!"M00114000025»M00114000026»M00115000022»M00115000027".contains(promoid)){
                    //update of inventory is perform here!
                    //if(!updateSPInventory((String) poData.get("sPromoIDx"),  CommonUtils.toDate((String)poData.get("dPickupxx")), true, (String) poData.get("sTransNox"), Integer.parseInt((String)poData.get("nItemQtyx")))){
                    if(!updateSPInventory((String) promoid,  CommonUtils.toDate((String)poData.get("dPickupxx")), true, (String) poData.get("sTransNox"), Integer.parseInt((String) loJSON.get("nItemQtyx")))){
                        if(!pbWithParnt){
                            poGRider.rollbackTrans();
                        }
                        return false;
                    }
                }

                //END OF: iloop ko ulit
            }
            
            //old code:
            //lsSQL = "UPDATE G_Card_Order_Master" + 
            //       " SET cTranStat = '2'" + 
            //       " WHERE sTransNox = " + SQLUtil.toSQL(source);
            
            //mac 2019.07.23
            //  update the pickup date on master and save the OTP used in the transaction.
            lsSQL = "UPDATE G_Card_Order_Master" + 
                    " SET cTranStat = '2'" +
                        ", sApprCode = " + SQLUtil.toSQL((String) poData.get("sApprCode")) + 
                        ", sOTPasswd = " + SQLUtil.toSQL((String) poData.get("sOTPasswd")) + 
                        ", dPickUpxx = " + SQLUtil.toSQL(poGRider.getServerDate()) +
                    " WHERE sTransNox = " + SQLUtil.toSQL(source);
            
            
            poGRider.executeQuery(lsSQL, "G_Card_Order_Master", "", "");
            if(!poGRider.getErrMsg().isEmpty()){
                psMessage = poGRider.getErrMsg();
                if(!pbWithParnt){
                    poGRider.rollbackTrans();
                }
                return false;         
            }

            lsSQL = "INSERT INTO G_Card_Order_Master_Digital" +
                   " SET sTransNox = " + SQLUtil.toSQL(source) +
                      ", sIMEINoxx = " + SQLUtil.toSQL((String)poGCDevice.getCardInfo("sIMEINoxx")) +
                      ", sUserIDxx = " + SQLUtil.toSQL((String)poGCDevice.getCardInfo("sUserIDxx")) +
                      ", sMobileNo = " + SQLUtil.toSQL((String)poGCDevice.getCardInfo("sMobileNo")) +
                      ", dQRDateTm = " + SQLUtil.toSQL((String)poGCDevice.getCardInfo("dQRDateTm"));
            
            if(poGRider.executeQuery(lsSQL, "G_Card_Order_Master_Digital", "", "") == 0){
                psMessage = poGRider.getErrMsg();
                if(!pbWithParnt){
                    poGRider.rollbackTrans();
                }
                return false;
            }
            
            if(!pbWithParnt){
                poGRider.commitTrans();
            }

            //send the update to the server...
            if(!pbWithParnt){
                poGRider.beginTrans();
            }

            long points = Double.valueOf((String) poData.get("nPointsxx")).longValue();
            JSONObject response = 
                    GCRestAPI.UpdatePoint(poGRider, 
                    (String) poGCDevice.getCardInfo("sCardNmbr"),
                    "PREORDER",
                    source,
                    points);
            String result = (String) response.get("result");

            if(result.equalsIgnoreCase("success")){
                String sql = "UPDATE G_Card_Order_Master" + 
                            " SET cPointSnt = '1'" + 
                            " WHERE sTransNox = " + SQLUtil.toSQL(source);
                poGRider.executeQuery(sql, "G_Card_Order_Master", "", "");
                if(!poGRider.getErrMsg().isEmpty()){
                    psMessage = poGRider.getErrMsg();
                    if(!pbWithParnt){
                          poGRider.rollbackTrans();
                    }
                    return false;
                }
            }
            
            if(!pbWithParnt){
                //poGRider.commitTrans();
            }
        }//end: if(poCtrl == null) - else
        
        setMessage("Transaction saved successfully...");
        return true;
    }
    
   private boolean issue_petron_value_card(){
      GCard loGCard = new GCard();
      loGCard.setGRider(poGRider);
      loGCard.setWithParent(true);
      loGCard.setBranch(psBranchCd);
      
      return loGCard.issue_petron_value_card((String)poData.get("sGCardNox"), (Date) poData.get("dPickupxx"), (String)poData.get("sRemarksx"));
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
    
    private boolean updateSPInventory(String sPromoIDx, Date dTransact, boolean bIsNew, String sTransNox, int qty){
        StringBuilder lsSQL;
        boolean lbSuccess = true;
        Connection loCon = poGRider.getConnection();
        System.out.println("updateSPInventory");
      
        lsSQL = new StringBuilder();
        lsSQL.append("SELECT sPartsIDx" + 
                        ", nQuantity" +
                    " FROM G_Card_Promo_Detail" + 
                    " WHERE sTransNox = " + SQLUtil.toSQL(sPromoIDx));
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(lsSQL.toString());
            System.out.println(lsSQL.toString());
         
            if(!loRS.next()){
                psMessage = "No detail found in the promo code!";
                lbSuccess = false;
            }
            else{
                while(!loRS.isAfterLast()){   
                    StringBuilder lsSQL1 = new StringBuilder();
                    lsSQL1.append("SELECT sPartsIDx" + 
                                        ", nQtyOnHnd" + 
                                        ", nLedgerNo" + 
                                    " FROM SP_Inventory" +      
                                    " WHERE sPartsIDx = " + SQLUtil.toSQL(loRS.getString("sPartsIDx")) +
                                        " AND sBranchCd = " + SQLUtil.toSQL(poGRider.getBranchCode()));
                    Statement loStmt1 = null;
                    ResultSet loRS1 = null;
              
                    try {
                        loStmt1 = loCon.createStatement();
                        loRS1 = loStmt1.executeQuery(lsSQL1.toString());

                        if(!loRS1.next()) {
                            psMessage = "No inventory found for " + loRS.getString("sPartsIDx") + "!";
                            lbSuccess = false;
                        }    
                        else{
                            while(!loRS1.isAfterLast()){
                                StringBuilder lsSQL2 = new StringBuilder();
                                lsSQL2.append("UPDATE SP_Inventory SET" +
                                                    "  nQtyOnHnd = nQtyOnHnd" + (bIsNew ? " - " : " + ") + (loRS.getInt("nQuantity") * qty) +
                                                    ", nLedgerNo = " + SQLUtil.toSQL(String.format("%0" + pxeLdgrSize + "d", Integer.parseInt(loRS1.getString("nLedgerNo")) + 1)) + 
                                                " WHERE sPartsIDx = " + SQLUtil.toSQL(loRS1.getString("sPartsIDx")) +
                                                    " AND sBranchCd = " + SQLUtil.toSQL(poGRider.getBranchCode()));
                                poGRider.executeQuery(lsSQL2.toString(), "SP_Inventory", "", "");
                                System.out.println(lsSQL2.toString());
                            
                                StringBuilder lsNme = new StringBuilder();
                                lsSQL2 = new StringBuilder();
                                System.out.println(dTransact);
                                lsNme.append("( sPartsIDx");
                                lsNme.append(", sBranchCd");
                                lsNme.append(", nLedgerNo");
                                lsNme.append(", dTransact");
                                lsNme.append(", sSourceCd");
                                lsNme.append(", sSourceNo");
                                lsNme.append(", nQtyInxxx");
                                lsNme.append(", nQtyOutxx");
                                lsNme.append(", nQtyIssue");
                                lsNme.append(", nQtyOrder");
                                lsNme.append(", nQtyOnHnd");
                                lsNme.append(", nResvOrdr");
                                lsNme.append(", nBackOrdr");
                                lsNme.append(", dModified)");
                            
                                lsSQL2.append("( " + SQLUtil.toSQL(loRS1.getString("sPartsIDx")));
                                lsSQL2.append(", " + SQLUtil.toSQL(poGRider.getBranchCode()));
                                lsSQL2.append(", " + SQLUtil.toSQL(String.format("%0" + pxeLdgrSize + "d", Integer.parseInt(loRS1.getString("nLedgerNo")) + 1)));
                                lsSQL2.append(", " + SQLUtil.toSQL(dTransact));
                                lsSQL2.append(", " + SQLUtil.toSQL(pxeSource));
                                lsSQL2.append(", " + SQLUtil.toSQL(sTransNox));
                                lsSQL2.append(", " + ((bIsNew ? 0 : loRS.getInt("nQuantity"))) * qty);
                                lsSQL2.append(", " + (bIsNew ? loRS.getInt("nQuantity") : 0));
                                lsSQL2.append(", " + 0);
                                lsSQL2.append(", " + 0);
                                lsSQL2.append(", " + (loRS1.getInt("nQtyOnHnd") - (loRS.getInt("nQuantity") * (bIsNew ? +1 : -1) * qty)));
                                lsSQL2.append(", " + 0);
                                lsSQL2.append(", " + 0);
                                lsSQL2.append(", " + SQLUtil.toSQL(poGRider.getServerDate(loCon)));
                                lsSQL2.append(")");
                      
                                poGRider.executeQuery("INSERT INTO SP_Inventory_Ledger" + lsNme.toString() + " VALUES " +  lsSQL2.toString(), "SP_Inventory_Ledger", "", "");
                                System.out.println(lsSQL2.toString());

                                loRS1.next();
                            }
                        }
                    } catch (SQLException ex) {
                        psMessage = ex.getMessage();
                        lbSuccess = false;
                    } finally{
                        MiscUtil.close(loRS1);
                        MiscUtil.close(loStmt1);
                    }
                
                    if(!lbSuccess)
                        return lbSuccess;
                    else
                        loRS.next(); 
                }
            }
        } catch (SQLException ex) {
            psMessage = ex.getMessage();
            lbSuccess = false;
        } finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
       
       return lbSuccess;
    }
   
    /**
     * GET PROMO
     * 
     * @param fsValue Promo ID
     * @return Redemption Promo Object
     */
    public XMGCRedemptionPromo getPromo(String fsValue){
        //public XMGCRedemptionPromo getPromo(String fsValue){
        
        if(poParts == null)
            poParts = new XMGCRedemptionPromo(poGRider, psBranchCd, true );

        //poParts.loadTransaction((String)poData.get("sPromoIDx"));
        poParts.loadTransaction(fsValue);
        return poParts;
    }
    
    public XMBranch getBranch(String fsBranchCd){
        XMBranch loBranch = new XMBranch(poGRider, psBranchCd, true);

        loBranch.openRecord(fsBranchCd);
        return loBranch;
    }
   
    public Object getDeviceInfo(){
        if(poGCDevice == null){
            return null;
        }

        return poGCDevice;
    }
    
    public Object getMaster(String value){
        if (poData.isEmpty())
            return null;
        
        if (!poData.containsKey(value))
            return null;
        
        return poData.get(value);
    }
    
    
    /**
     * GET ORDER DETAILS
     * 
     * Available keys: \n\t
     * nEntryNox \n\t
     * sPromoIDx \n\t
     * nItemQtyx \n\t
     * nPointsxx
     * 
     * @param fnRow specific row to retrieve
     * @param fsKey the key value
     * @return 
     */
    public Object getDetail(int fnRow, String fsKey){
        if (poDetail.isEmpty())
            return null;
        
        return poDetail.get(fnRow);
    }
    
    public int ItemCount(){
        if (poDetail.isEmpty()) return 0;
        
        return poDetail.size();
    }
    
    public XMGCard getGCard(){
       if(poGCard == null)
          poGCard = new XMGCard(poGRider, psBranchCd, true);

       poGCard.openRecord((String)poData.get("sGCardNox"));
       return poGCard;
    }
    
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
    
    private GRider poGRider;
    private GCardDevice poGCDevice;
    private XMGCard poGCard = null;
    private XMGCRedemptionPromo poParts = null;
    
    private int pnEditMode;
    private String psMessage;
    private String psBranchCd;
    private boolean pbWithParnt = false;
    private JSONObject poData = null;
    private ArrayList<JSONObject> poDetail = null;

    private static String pxeSource = "SPGc"; //GCard Redemption
    private static int pxeLdgrSize = 6;       //nLedgerNo field size 
}
