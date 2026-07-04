import { http } from './http';
export interface DashboardOverview{todayAppointments:number;waitingVisits:number;activeDoctors:number;scheduledRooms:number;totalRooms:number;roomCoverageRate:number;aiTriageCount:number;departmentLoads:Array<{name:string;value:number}>}
export async function getDashboardOverview(){return(await http.get<DashboardOverview>('/dashboard/overview')).data;}
