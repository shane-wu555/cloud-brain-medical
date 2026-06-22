import { http } from './http';
export interface MedicalOrder{id:string;appointmentId:string;patientId:string;patientName:string;orderingDoctorId:string;orderType:'CHECK'|'LAB'|'DISPOSAL';projectCode:string;projectName:string;purpose?:string;bodyPart?:string;amount:number;paymentStatus:string;status:string;executorId?:string;executorName?:string;executionLocation?:string;equipmentId?:string;queueNumber?:number;urgency:string;triageReasons?:string;missedCount:number;resultSummary?:string}
export interface MedicalReport{id:string;medicalOrderId:string;reportType:string;status:string;findings:string;conclusion:string;advice:string;createdByType:string;aiTaskId?:string;modifiedFromAi:boolean;confirmedBy?:string;confirmedAt?:string}
export interface MedicalItem{code:string;name:string;category:'CHECK'|'LAB'|'DISPOSAL'|'DRUG';price:number}
export async function getMedicalItems(){return(await http.get<MedicalItem[]>('/catalog/medical-items')).data}
export async function getMedicalOrders(params:Record<string,string|undefined>={}){return(await http.get<MedicalOrder[]>('/medical-orders',{params})).data}
export async function createMedicalOrder(payload:Record<string,unknown>){return(await http.post<MedicalOrder>('/medical-orders',payload)).data}
export async function startMedicalOrder(id:string){return(await http.post<MedicalOrder>(`/medical-orders/${id}/start`)).data}
export async function missMedicalOrder(id:string){return(await http.post<MedicalOrder>(`/medical-orders/${id}/miss`)).data}
export async function completeMedicalOrder(id:string,payload:Record<string,unknown>){return(await http.post<MedicalOrder>(`/medical-orders/${id}/complete`,payload)).data}
export async function createReportDraft(id:string,payload:{findings:string;conclusion:string;advice:string}){return(await http.post<MedicalReport>(`/medical-orders/${id}/reports/draft`,payload)).data}
export async function confirmReport(id:string,payload:{findings:string;conclusion:string;advice:string}){return(await http.post<MedicalReport>(`/medical-orders/${id}/reports/confirm`,payload)).data}
export async function getReports(){return(await http.get<MedicalReport[]>('/medical-orders/reports')).data}
export async function uploadAttachment(id:string,file:File){const data=new FormData();data.append('file',file);return(await http.post<{id:string}>(`/medical-orders/${id}/attachments`,data)).data}
export async function submitCt(id:string,attachmentId:string){return(await http.post<{externalTaskId:string;status:string}>(`/medical-orders/${id}/ct-analysis`,{attachmentId})).data}
export async function refreshAiTask(taskId:string){return(await http.get<{externalTaskId:string;status:string}>(`/medical-orders/ai-tasks/${taskId}`)).data}
export async function createSpecimen(orderId:string,specimenType:string,barcode:string){return(await http.post<{id:string;status:string}>(`/medical-orders/${orderId}/specimens`,{specimenType,barcode})).data}
export async function transitionSpecimen(id:string,status:string,reason=''){return(await http.post(`/medical-orders/specimens/${id}/status`,{status,reason})).data}
export async function saveLabResults(orderId:string,specimenId:string,items:Array<Record<string,unknown>>){return(await http.post(`/medical-orders/${orderId}/laboratory-results`,{specimenId,items})).data}
