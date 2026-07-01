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
  roomId?: string;
  roomName?: string;
  specialty: string;
  roleType: string;
}

export interface DoctorEvent {
  id: string;
  doctorId: string;
  doctorName: string;
  departmentName: string;
  eventType: 'LEAVE' | 'SURGERY';
  dates: string[];
  periods: string[];
  note: string;
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
  roomId?: string;
  roomName?: string;
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
  roomId?: string;
  roomName?: string;
  specialty: string;
  weeklyCapacity: number;
  historicalAverageVisits?: number;
  unavailableSlots?: Array<{ date: string; period: string; type: 'LEAVE' | 'SURGERY' }>;
}

export interface AiScheduleDemand {
  departmentId: string;
  roomId?: string;
  roomName?: string;
  workDate: string;
  period: string;
  expectedVisits: number;
  historicalVisits?: number | null;
}

export interface AiScheduleSuggestion {
  suggestionId: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  roomId?: string;
  roomName?: string;
  workDate: string;
  period: string;
  capacity: number;
  requiresAdminConfirmation: boolean;
}

export interface AiScheduleResponse {
  aiRecordId: string | null;
  suggestions: AiScheduleSuggestion[];
  provider?: string | null;
  model?: string | null;
  fallbackUsed?: boolean;
  backgroundSummary?: string | null;
  knowledgeSources?: Array<{
    sourceId?: string;
    sourceType?: string;
    businessId?: string | null;
    title?: string;
    content?: string;
    score?: number | null;
  }>;
}

export async function getDepartments() {
  return (await http.get<Department[]>('/departments')).data;
}

export async function getDoctors(departmentId?: string) {
  return (await http.get<Doctor[]>('/doctors', { params: { departmentId } })).data;
}

export async function getDoctor(id: string) {
  return (await http.get<Doctor>(`/doctors/${id}`)).data;
}

export async function updateDoctor(
  id: string,
  payload: Pick<Doctor, 'name' | 'title' | 'departmentId' | 'specialty'>
) {
  return (await http.put<Doctor>(`/doctors/${id}`, payload)).data;
}

export async function getDoctorEvents() {
  return (await http.get<DoctorEvent[]>('/doctors/events')).data;
}

export async function createDoctorEvent(payload: {
  doctorId: string;
  eventType: DoctorEvent['eventType'];
  dates: string[];
  periods: string[];
  note: string;
}) {
  return (await http.post<DoctorEvent>('/doctors/events', payload)).data;
}

export async function updateDoctorEvent(
  id: string,
  payload: {
    doctorId: string;
    eventType: DoctorEvent['eventType'];
    dates: string[];
    periods: string[];
    note: string;
  }
) {
  return (await http.put<DoctorEvent>(`/doctors/events/${id}`, payload)).data;
}

export async function deleteDoctorEvent(id: string) {
  await http.delete(`/doctors/events/${id}`);
}

export async function getSchedules(params: { doctorId?: string; departmentId?: string; bookingWindowOnly?: boolean } = {}) {
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

export async function getAiReplanPreview(params: {
  departmentId?: string;
  baseVisits?: number;
  weekdayPeak?: boolean;
  weekdayIncrease?: number;
  morningPeak?: boolean;
  morningIncrease?: number;
  force?: boolean;
} = {}) {
  return (await http.get<AiScheduleResponse>('/schedules/ai-replan-preview', { params, timeout: 180000 })).data;
}

export async function publishAiScheduleSuggestion(
  suggestionId: string,
  payload: AiScheduleSuggestion & { aiRecordId?: string | null }
) {
  return (await http.post<Schedule>(`/schedules/ai-suggestions/${suggestionId}/publish`, payload)).data;
}

export async function publishAiScheduleSuggestions(payload: {
  aiRecordId?: string | null;
  suggestions: Array<AiScheduleSuggestion & { aiRecordId?: string | null }>;
}) {
  return (await http.post<Schedule[]>('/schedules/ai-suggestions/publish-batch', payload)).data;
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
