package com.miniESB.domain.entity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "consumers")
public class Consumer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name="endpoint", nullable = false, length = 500)
    private String endpoint;

    @OneToMany(mappedBy = "consumer", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Pipeline> pipelines = new ArrayList<>();
    
    public Consumer() {}

    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}

    public String getName(){return name;}
    public void setName(String name){this.name=name;}
    
    public String getEndpoint(){return endpoint;}
    public void SetEndpoint(String endpoint){this.endpoint=endpoint;}

    public List<Pipeline> getPipelines() {return pipelines;}
    public void setPipelines(List<Pipeline> pipelines) {this.pipelines=pipelines;}


    
}
