package com.miniESB.domain.entity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="providers")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column( name="name", nullable = false, length = 150)
    private String name;

    @Column( name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column (name= "protocol" , nullable = false, length = 50)
    private String protocol;

    @Column( name = "timeout", nullable =false )
    private int timeout;

    @OneToMany(mappedBy = "provider" , cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Pipeline> pipelines = new ArrayList<>();
    public Provider() {}
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
 
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
 
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
 
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
 
    public List<Pipeline> getPipelines() { return pipelines; }
    public void setPipelines(List<Pipeline> pipelines) { this.pipelines = pipelines; }


    
}
