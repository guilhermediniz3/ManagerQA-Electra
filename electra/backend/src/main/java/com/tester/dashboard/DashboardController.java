package com.tester.dashboard;
import java.time.*;import java.time.format.*;import java.util.*;import java.util.function.Function;import com.tester.demand.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/dashboard") public class DashboardController {
 private final DemandService demands;public DashboardController(DemandService demands){this.demands=demands;}
 public record Group(String name,long value){}public record Health(String name,long tasks,long returns,double concentration){}
 public record View(long total,long blocked,long returned,long awaitingDeploy,long approved,List<Group> byDeveloper,List<Group> byTester,List<Group> byTechnology,List<Group> byStatus,List<Health> modules){}
 @GetMapping public View get(@RequestParam(required=false)String squad,@RequestParam(required=false)String sprint,@RequestParam(required=false)LocalDate start,@RequestParam(required=false)LocalDate end){
  if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("Data inicial deve preceder a final.");
  var rows=demands.list().stream().filter(d->squad==null||squad.isBlank()||squad.equals(d.squad())).filter(d->sprint==null||sprint.isBlank()||sprint.equals(d.sprint())).filter(d->{if(start==null&&end==null)return true;try{var date=jiraUpdatedDate(d.jiraUpdated());return(start==null||!date.isBefore(start))&&(end==null||!date.isAfter(end));}catch(Exception e){return false;}}).toList();
  var modules=rows.stream().collect(java.util.stream.Collectors.groupingBy(d->name(d.moduleName()))).entrySet().stream().map(e->{long count=e.getValue().stream().filter(d->status(d,"Retorno")).count();return new Health(e.getKey(),e.getValue().size(),count,100.0*count/e.getValue().size());}).sorted(Comparator.comparingDouble(Health::concentration).reversed()).toList();
  long approved=count(rows,"Aguardando Deploy");return new View(rows.size(),count(rows,"Bloqueado"),count(rows,"Retorno"),approved,approved,groups(rows,DemandService.View::assigneeName),groups(rows,DemandService.View::testerName),groups(rows,DemandService.View::technologyName),groups(rows,DemandService.View::statusName),modules);
 }
 private static final DateTimeFormatter JIRA_UPDATED_FORMAT = new DateTimeFormatterBuilder()
  .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME).appendOffset("+HHMM", "+0000").toFormatter();
 private static LocalDate jiraUpdatedDate(String value){
  try{return OffsetDateTime.parse(value).toLocalDate();}
  catch(DateTimeParseException e){return OffsetDateTime.parse(value,JIRA_UPDATED_FORMAT).toLocalDate();}
 }
 private static String name(String value){return value==null||value.isBlank()?"Não informado":value;}
 private static boolean status(DemandService.View d,String status){return status.equalsIgnoreCase(d.statusName());}
 private static long count(List<DemandService.View> rows,String status){return rows.stream().filter(d->status(d,status)).count();}
 private static List<Group> groups(List<DemandService.View> rows,Function<DemandService.View,String> key){return rows.stream().collect(java.util.stream.Collectors.groupingBy(d->name(key.apply(d)),java.util.stream.Collectors.counting())).entrySet().stream().map(e->new Group(e.getKey(),e.getValue())).sorted(Comparator.comparingLong(Group::value).reversed()).toList();}
}
