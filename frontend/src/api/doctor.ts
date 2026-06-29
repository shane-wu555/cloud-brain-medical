import { http } from './http';

export interface Department {
  id: string;
  name: string;
  description: string;
}

export interface Doctor {
  id: string;
  employeeNo: string;
  name: string;
  title: string;
  departmentId: string;
  departmentName: string;
  specialty: string;
  roleType: string;
}

export interface ScheduleTimeSlot {
  id: string;
  startTime: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
}

export interface Schedule {
  id: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
  booked: number;
  locked: number;
  available: number;
  status: string;
  timeSlots?: ScheduleTimeSlot[];
}

export interface AiDoctorCandidate {
  doctorId: string;
  doctorName: string;
  departmentId: string;
  specialty: string;
  weeklyCapacity: number;
  leaveDates: string[];
  surgeryDates: string[];
}

export interface AiScheduleDemand {
  departmentId: string;
  workDate: string;
  period: string;
  expectedVisits: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface AiScheduleSuggestion {
  suggestionId: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
  reason: string;
  requiresAdminConfirmation: boolean;
}

export interface AiScheduleResponse {
  aiRecordId: string | null;
  suggestions: AiScheduleSuggestion[];
}

export async function getDepartments() {
  return (await http.get<Department[]>('/departments')).data;
}

export async function getDoctors(departmentId?: string) {
  return (await http.get<Doctor[]>('/doctors', { params: { departmentId } })).data;
}

export async function getSchedules(params: { doctorId?: string; departmentId?: string } = {}) {
  return (await http.get<Schedule[]>('/schedules', { params })).data;
}

export async function createSchedule(payload: {
  doctorId: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
}) {
  return (await http.post<Schedule>('/schedules', payload)).data;
}

export async function getAiScheduleSuggestions(payload: {
  candidates: AiDoctorCandidate[];
  demands: AiScheduleDemand[];
}) {
  return (await http.post<AiScheduleResponse>('/schedules/ai-suggestions', payload)).data;
}

export async function publishAiScheduleSuggestion(
  suggestionId: string,
  payload: AiScheduleSuggestion & { aiRecordId?: string | null }
) {
  return (await http.post<Schedule>(`/schedules/ai-suggestions/${suggestionId}/publish`, payload)).data;
}

export async function suspendSchedule(id: string, reason: string) {
  return (await http.put<Schedule>(`/schedules/${id}/suspend`, { reason })).data;
}

export async function reschedule(id: string, workDate: string, period: string) {
  return (await http.put<Schedule>(`/schedules/${id}/reschedule`, { workDate, period })).data;
}

export async function createDepartment(payload: { name: string; description: string }) {
  return (await http.post<Department>('/departments', payload)).data;
}

export async function createDoctor(payload: {
  employeeNo: string;
  name: string;
  title: string;
  departmentId: string;
  roleType: string;
  specialty: string;
}) {
  return (await http.post<Doctor>('/doctors', payload)).data;
}
